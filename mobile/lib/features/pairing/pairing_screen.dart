import 'dart:async';

import 'package:flutter/services.dart';
import 'package:material_ui/material_ui.dart';

import 'pairing_controller.dart';

class PairingScreen extends StatefulWidget {
  const PairingScreen({super.key, required this.controller});
  final PairingController controller;
  @override
  State<PairingScreen> createState() => _PairingScreenState();
}

class _PairingScreenState extends State<PairingScreen>
    with WidgetsBindingObserver {
  final _address = TextEditingController(),
      _fingerprint = TextEditingController();
  final _pin = TextEditingController(),
      _name = TextEditingController(text: 'Family phone');
  PairingController get c => widget.controller;
  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    unawaited(c.initialise());
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state != AppLifecycleState.resumed) {
      unawaited(c.pauseDiscovery());
      _pin.clear();
    }
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    for (final field in [_address, _fingerprint, _pin, _name]) {
      field.dispose();
    }
    super.dispose();
  }

  Widget _field(
    TextEditingController field,
    String label, {
    String? hint,
    bool secret = false,
    int? max,
  }) => Padding(
    padding: const EdgeInsets.only(bottom: 18),
    child: TextField(
      controller: field,
      enabled: !c.busy,
      obscureText: secret,
      autocorrect: false,
      enableSuggestions: false,
      maxLength: max,
      keyboardType: secret ? TextInputType.number : TextInputType.text,
      inputFormatters: secret ? [FilteringTextInputFormatter.digitsOnly] : null,
      decoration: InputDecoration(
        labelText: label,
        hintText: hint,
        border: const OutlineInputBorder(),
      ),
    ),
  );
  Future<void> _forget() async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Forget this phone’s connection?'),
        content: const Text(
          'This removes the saved key here. To revoke its access on the TV too, use Revoke all phone access in Family TV settings.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const Text('Cancel'),
          ),
          TextButton(
            onPressed: () => Navigator.pop(context, true),
            child: const Text('Forget'),
          ),
        ],
      ),
    );
    if (confirmed == true) await c.forget();
  }

  @override
  Widget build(BuildContext context) => ListenableBuilder(
    listenable: c,
    builder: (context, _) => Scaffold(
      appBar: AppBar(
        title: const Text('Family TV Remote'),
        actions: [
          IconButton(
            tooltip: 'About and licenses',
            icon: const Icon(Icons.info_outline),
            onPressed: () => showAboutDialog(
              context: context,
              applicationName: 'Family TV Remote',
              applicationVersion: '0.1.0',
              children: [
                const Text(
                  'Pairing preview. Remote controls arrive in a later milestone.',
                ),
              ],
            ),
          ),
        ],
      ),
      body: SafeArea(
        child: Center(
          child: ConstrainedBox(
            constraints: const BoxConstraints(maxWidth: 560),
            child: ListView(
              padding: const EdgeInsets.all(24),
              children: [
                const Icon(Icons.connected_tv, size: 64),
                const SizedBox(height: 20),
                Text(switch (c.phase) {
                  PairPhase.choose => 'Your TV. Your family.',
                  PairPhase.pin => 'Enter the TV PIN',
                  PairPhase.paired => 'Paired with Family TV',
                  PairPhase.saved => 'Your saved TV',
                }, style: Theme.of(context).textTheme.headlineMedium),
                const SizedBox(height: 12),
                if (c.busy)
                  const LinearProgressIndicator(
                    semanticsLabel: 'Working on your connection',
                  ),
                if (c.error != null)
                  Padding(
                    padding: const EdgeInsets.symmetric(vertical: 16),
                    child: Semantics(
                      liveRegion: true,
                      child: Text(
                        c.error!.message,
                        style: TextStyle(
                          color: Theme.of(context).colorScheme.error,
                        ),
                      ),
                    ),
                  ),
                if (c.error?.code == 'storage')
                  TextButton(
                    onPressed: c.busy ? null : _forget,
                    child: const Text('Forget saved connection'),
                  ),
                if (c.phase == PairPhase.choose) ...[
                  const Text(
                    'Connect your phone and Fire Stick to the same home Wi-Fi. On TV, enable Remote access in Family TV settings.',
                  ),
                  const SizedBox(height: 20),
                  OutlinedButton.icon(
                    onPressed: c.busy || c.scanning ? null : c.discover,
                    icon: const Icon(Icons.wifi_find),
                    label: Text(
                      c.scanning ? 'Looking for Family TV…' : 'Find my TV',
                    ),
                  ),
                  for (final tv in c.candidates)
                    ListTile(
                      leading: const Icon(Icons.tv),
                      title: Text(tv.name),
                      subtitle: Text(tv.endpoint.toString()),
                      onTap: c.busy
                          ? null
                          : () {
                              _address.text = tv.endpoint.toString();
                            },
                    ),
                  const SizedBox(height: 16),
                  const Text(
                    'No TV listed? Enter the address manually. In phone Settings, allow local network access if prompted or previously denied.',
                  ),
                  const SizedBox(height: 20),
                  _field(
                    _address,
                    'TV address and port',
                    hint: '192.168.1.20:48322',
                  ),
                  const Text(
                    'Open Remote connection details on the TV and copy its full SHA-256 fingerprint below. Discovery alone does not confirm the TV’s identity.',
                  ),
                  const SizedBox(height: 16),
                  _field(_fingerprint, 'TV certificate fingerprint'),
                  const Text(
                    'Then open Pair a phone on the TV. Its PIN lasts two minutes.',
                  ),
                  const SizedBox(height: 16),
                  FilledButton(
                    onPressed: c.busy
                        ? null
                        : () => c.checkTv(_address.text, _fingerprint.text),
                    child: const Text('Confirm TV and continue'),
                  ),
                ],
                if (c.phase == PairPhase.pin) ...[
                  Text(
                    'TV: ${c.endpoint}. Enter the six-digit PIN shown in Family TV. Keep the pairing window open.',
                  ),
                  const SizedBox(height: 24),
                  _field(_name, 'Name for this phone', max: 48),
                  _field(_pin, 'Six-digit TV PIN', secret: true, max: 6),
                  FilledButton(
                    onPressed: c.busy
                        ? null
                        : () async {
                            final pin = _pin.text;
                            _pin.clear();
                            await c.pair(pin, _name.text);
                          },
                    child: const Text('Pair this phone'),
                  ),
                  TextButton(
                    onPressed: c.busy
                        ? null
                        : () {
                            _pin.clear();
                            c.changeTv();
                          },
                    child: const Text('Refresh PIN window or change TV'),
                  ),
                ],
                if (c.phase == PairPhase.paired ||
                    c.phase == PairPhase.saved) ...[
                  Text('TV: ${c.credential?.endpoint}'),
                  const SizedBox(height: 16),
                  Text(
                    c.phase == PairPhase.paired
                        ? 'Your pairing key is saved securely on this phone.'
                        : 'A pairing key is saved on this phone. The TV’s current reachability and token validity have not been checked.',
                  ),
                  const SizedBox(height: 16),
                  const Text(
                    'D-pad and app controls are coming next. This build cannot navigate the TV or other apps.',
                  ),
                  const SizedBox(height: 24),
                  OutlinedButton(
                    onPressed: c.busy ? null : _forget,
                    child: const Text('Forget saved connection'),
                  ),
                ],
                const SizedBox(height: 28),
                const Text(
                  'Local Wi-Fi only • No account or cloud relay',
                  textAlign: TextAlign.center,
                ),
              ],
            ),
          ),
        ),
      ),
    ),
  );
}
