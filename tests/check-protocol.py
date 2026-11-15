"""Executable protocol contract checks, not a runtime server implementation."""
import json
from pathlib import Path
from jsonschema import Draft202012Validator
from openapi_spec_validator import validate

ROOT = Path(__file__).resolve().parents[1]
protocol = ROOT / 'protocol'
path = protocol / 'openapi.yaml'
spec = json.loads('\n'.join(path.read_text().splitlines()[1:]))
validate(spec, base_uri=path.as_uri())
schema = json.loads((protocol / 'events.schema.json').read_text())
Draft202012Validator.check_schema(schema)
validators = {'event': Draft202012Validator(schema)}
for name in schema['$defs']:
    validators[name] = Draft202012Validator({'$defs': schema['$defs'], '$ref': '#/$defs/' + name})
# Catch accidental removal of authentication from ordinary endpoints.
assert spec['security'] == [{'phoneToken': []}]
for route, methods in spec['paths'].items():
    for method, operation in methods.items():
        if method not in ('get', 'post', 'put', 'delete'):
            continue
        if route not in ('/v1/info', '/v1/pair'):
            assert operation.get('security', spec['security']) == [{'phoneToken': []}]
for route in ('/v1/rows', '/v1/hidden'):
    assert any(p['name'] == 'If-Match' and p['required'] for p in spec['paths'][route]['put']['parameters'])
cases = json.loads((protocol / 'fixtures/messages.json').read_text())
assert len({c['name'] for c in cases}) == len(cases)
positive = negative = 0
for case in cases:
    value = case['value']
    valid = validators[case['schema']].is_valid(value)
    # Cross-field invariants JSON Schema cannot express directly; fixtures will
    # also run against the real Java dispatcher once that exists.
    if valid and case['schema'] == 'Rows':
        packages = [p for row in value['rows'] for p in row['packages']]
        valid = len(packages) == len(set(packages))
    if valid and case['schema'] == 'event' and value['type'] in ('action', 'ack'):
        valid = value['id'] == value['payload']['id']
    assert valid == case['valid'], case['name']
    if valid:
        positive += 1
    else:
        negative += 1
assert positive > 0 and negative > 0
print(f'PASS: OpenAPI, schema, auth/revision guards, {positive} positive and {negative} negative fixtures')
