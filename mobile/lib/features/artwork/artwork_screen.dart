import 'dart:async';
import 'dart:math' as math;
import 'dart:ui' as ui;

import 'package:flutter/services.dart';
import 'package:material_ui/material_ui.dart';

import '../../core/api/apps_api.dart';
import '../../core/api/models.dart';
import 'artwork_api.dart';

class ArtworkScreen extends StatefulWidget {
  const ArtworkScreen({super.key, required this.apps, required this.api});
  final List<TvApp> apps;
  final ArtworkApi api;
  @override
  State<ArtworkScreen> createState() => _ArtworkScreenState();
}

class _ArtworkScreenState extends State<ArtworkScreen>
    with WidgetsBindingObserver {
  static const picker = MethodChannel('familytv/artwork-picker');
  Uint8List? bytes;
  ui.Image? preview;
  String? pkg;
  String kind = 'tile',
      message = 'Choose a PNG or JPEG up to 2 MiB and 8 megapixels.';
  double x = .5, y = .5, progress = 0;
  bool busy = false, committing = false, picking = false;
  int generation = 0;
  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    pkg = widget.apps.isEmpty ? null : widget.apps.first.package;
  }

  @override
  void dispose() {
    generation++;
    widget.api.cancel();
    preview?.dispose();
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.paused ||
        state == AppLifecycleState.detached) {
      if (!picking) {
        generation++;
        widget.api.cancel();
        if (mounted) {
          setState(() {
            busy = false;
            message = 'Connection paused. Check TV artwork before retrying.';
          });
        }
      }
    }
  }

  Future<void> pick() async {
    final stamp = ++generation;
    setState(() => picking = true);
    ui.ImmutableBuffer? buffer;
    ui.ImageDescriptor? descriptor;
    ui.Codec? codec;
    try {
      final value = await picker.invokeMethod<Uint8List>('pick');
      if (value == null || !mounted || stamp != generation) return;
      if (value.length < 8 ||
          value.length > 2097152 ||
          !(value[0] == 137 &&
                  value[1] == 80 &&
                  value[2] == 78 &&
                  value[3] == 71 ||
              value[0] == 255 && value[1] == 216 && value[2] == 255)) {
        throw const AppFailure('image', 'Choose a PNG or JPEG up to 2 MiB.');
      }
      buffer = await ui.ImmutableBuffer.fromUint8List(value);
      descriptor = await ui.ImageDescriptor.encoded(buffer);
      if (descriptor.width < 1 ||
          descriptor.height < 1 ||
          descriptor.width * descriptor.height > 8000000) {
        throw const AppFailure('image', 'Image must be at most 8 megapixels.');
      }
      final scale = math.min(
        1.0,
        512 / math.max(descriptor.width, descriptor.height),
      );
      codec = await descriptor.instantiateCodec(
        targetWidth: (descriptor.width * scale).round().clamp(1, 512),
        targetHeight: (descriptor.height * scale).round().clamp(1, 512),
      );
      final frame = await codec.getNextFrame();

      if (!mounted || stamp != generation) {
        frame.image.dispose();
        return;
      }
      setState(() {
        preview?.dispose();
        preview = frame.image;
        bytes = value;
        message = 'Preview the crop, then upload. Existing TV-local artwork takes priority.';
        x = y = .5;
      });
    } catch (e) {
      if (mounted && stamp == generation) {
        setState(
          () => message = e is AppFailure
              ? e.message
              : 'Cannot read this image. Choose a smaller PNG or JPEG.',
        );
      }
    } finally {
      codec?.dispose();
      descriptor?.dispose();
      buffer?.dispose();
      if (mounted) setState(() => picking = false);
    }
  }

  Future<void> upload() async {
    if (bytes == null || pkg == null || busy) return;
    final stamp = ++generation;
    setState(() {
      busy = true;
      committing = false;
      progress = 0;
      message = 'Uploading…';
    });
    try {
      await widget.api.upload(
        pkg!,
        kind,
        bytes!,
        (x * 1000).round(),
        (y * 1000).round(),
        progress: (value) {
          if (mounted && stamp == generation) setState(() => progress = value);
        },
        committing: () {
          if (mounted && stamp == generation) {
            setState(() {
              committing = true;
              message = 'Confirming replacement…';
            });
          }
        },
      );
      if (mounted && stamp == generation) {
        setState(() => message = 'Artwork saved.');
      }
    } catch (e) {
      if (mounted && stamp == generation) {
        setState(
          () => message = e is AppFailure
              ? e.message
              : 'Could not confirm upload. Check TV artwork before retrying.',
        );
      }
    } finally {
      if (mounted && stamp == generation) {
        setState(() {
          busy = false;
          committing = false;
        });
      }
    }
  }

  Future<void> reset() async {
    final yes = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Reset uploaded artwork?'),
        content: const Text(
          'The TV will use its existing local or default artwork.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const Text('Cancel'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(context, true),
            child: const Text('Reset'),
          ),
        ],
      ),
    );
    if (yes != true || !mounted || pkg == null) return;
    setState(() => busy = true);
    try {
      await widget.api.reset(pkg!, kind);
      if (mounted) setState(() => message = 'Uploaded artwork reset.');
    } catch (e) {
      if (mounted) {
        setState(
          () => message = e is AppFailure
              ? e.message
              : 'Reset could not be confirmed.',
        );
      }
    } finally {
      if (mounted) setState(() => busy = false);
    }
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(title: const Text('TV artwork')),
    body: SafeArea(
      child: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          DropdownButtonFormField<String>(
            initialValue: pkg,
            decoration: const InputDecoration(labelText: 'TV app'),
            items: [
              for (final app in widget.apps)
                DropdownMenuItem(value: app.package, child: Text(app.label)),
            ],
            onChanged: busy ? null : (v) => setState(() => pkg = v),
          ),
          SegmentedButton<String>(
            segments: const [
              ButtonSegment(value: 'tile', label: Text('App tile')),
              ButtonSegment(value: 'backdrop', label: Text('Backdrop')),
            ],
            selected: {kind},
            onSelectionChanged: busy
                ? null
                : (v) => setState(() => kind = v.first),
          ),
          if (preview != null)
            AspectRatio(
              aspectRatio: kind == 'tile' ? 16 / 9 : 8 / 3,
              child: ColoredBox(
                color: Colors.black,
                child: RawImage(
                  image: preview,
                  fit: BoxFit.cover,
                  alignment: Alignment(x * 2 - 1, y * 2 - 1),
                ),
              ),
            ),
          if (preview != null) ...[
            const Text('Horizontal crop'),
            Slider(
              value: x,
              onChanged: busy ? null : (v) => setState(() => x = v),
            ),
            const Text('Vertical crop'),
            Slider(
              value: y,
              onChanged: busy ? null : (v) => setState(() => y = v),
            ),
          ],
          Semantics(liveRegion: true, child: Text(message)),
          OutlinedButton(
            onPressed: busy || picking ? null : pick,
            child: Text(picking ? 'Choosing image…' : 'Choose image'),
          ),
          FilledButton(
            onPressed: busy || picking || bytes == null || pkg == null
                ? null
                : upload,
            child: const Text('Upload artwork'),
          ),
        ],
      ),
    ),
  );
}
