import 'dart:async';
import 'dart:convert';

import 'package:avgps/utils.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

class HomeWidget extends StatefulWidget {
  final LocationData? location;
  final List<Client> clients;
  final Settings settings;

  const HomeWidget(
      {Key? key,
      required this.location,
      required this.clients,
      required this.settings})
      : super(key: key);

  @override
  State<HomeWidget> createState() => _HomeWidgetState();
}

class _HomeWidgetState extends State<HomeWidget> {
  final _platform = const MethodChannel('avgps.youmu.moe/rpc');

  Timer? _timer;
  DateTime _reference = DateTime.now();

  @override
  void initState() {
    super.initState();

    _timer = Timer.periodic(
        const Duration(seconds: 1),
        (timer) => setState(() {
              _reference = DateTime.now();
            }));
  }

  @override
  void dispose() {
    super.dispose();

    _timer?.cancel();
  }

  String calcSince(DateTime? since) {
    if (since != null) {
      var fixSinceDuration = _reference.difference(since);
      if (fixSinceDuration.isNegative) {
        fixSinceDuration = const Duration();
      }
      return fixSinceDuration.inHours != 0
          ? "${fixSinceDuration.inHours}h ago"
          : (fixSinceDuration.inMinutes != 0
              ? "${fixSinceDuration.inMinutes}m ago"
              : "${fixSinceDuration.inSeconds}s ago");
    } else {
      return "Never";
    }
  }

  @override
  Widget build(BuildContext context) {
    // TODO: implement build

    final widget = context.widget as HomeWidget;
    final hasLocation = widget.location != null;

    return Container(
        padding: const EdgeInsets.fromLTRB(10, 20, 10, 20),
        width: double.maxFinite,
        child: Column(
          mainAxisAlignment: MainAxisAlignment.start,
          children: <Widget>[
            Card(
                child: Padding(
                    padding: const EdgeInsets.all(15),
                    child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: <Widget>[
                          LabelBox(
                              label: "coordinates",
                              child: hasLocation
                                  ? SelectableText(
                                      widget.location!.formatCoord(),
                                      style: Theme.of(context)
                                          .textTheme
                                          .headlineSmall)
                                  : Text("N/A",
                                      style: Theme.of(context)
                                          .textTheme
                                          .displaySmall)),
                          const SizedBox(height: 10),
                          Row(
                            mainAxisAlignment: MainAxisAlignment.spaceBetween,
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Column(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  LabelBox(
                                      label: "altitude",
                                      child: Text(
                                          hasLocation
                                              ? altitudeIn(
                                                  widget.location!.altitude,
                                                  widget.settings.altitudeUnit)
                                              : "N/A",
                                          style: Theme.of(context)
                                              .textTheme
                                              .headlineSmall)),
                                  const SizedBox(height: 10),
                                  LabelBox(
                                      label: "horizontal accuracy",
                                      child: Text(
                                          hasLocation
                                              ? horizontalIn(
                                                  widget.location!
                                                      .horizontalAccuracy,
                                                  widget
                                                      .settings.horizontalUnit)
                                              : "N/A",
                                          style: Theme.of(context)
                                              .textTheme
                                              .headlineSmall)),
                                ],
                              ),
                              Column(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  LabelBox(
                                      label: "speed",
                                      child: Text(
                                          hasLocation
                                              ? speedIn(widget.location!.speed,
                                                  widget.settings.speedUnit)
                                              : "N/A",
                                          style: Theme.of(context)
                                              .textTheme
                                              .headlineSmall)),
                                  const SizedBox(height: 10),
                                  LabelBox(
                                      label: "vertical accuracy",
                                      child: Text(
                                          hasLocation
                                              ? altitudeIn(
                                                  widget.location!
                                                      .verticalAccuracy,
                                                  widget.settings.altitudeUnit)
                                              : "N/A",
                                          style: Theme.of(context)
                                              .textTheme
                                              .headlineSmall)),
                                ],
                              ),
                              Column(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  LabelBox(
                                      label: "bearing",
                                      child: Text(
                                          hasLocation
                                              ? "${widget.location!.bearing.round()}°"
                                              : "N/A",
                                          style: Theme.of(context)
                                              .textTheme
                                              .headlineSmall)),
                                  const SizedBox(height: 10),
                                  LabelBox(
                                      label: "last fix",
                                      child: Text(
                                          calcSince(widget.location?.timestamp),
                                          style: Theme.of(context)
                                              .textTheme
                                              .headlineSmall)),
                                ],
                              )
                            ],
                          )
                        ]))),
            Expanded(
              child: Card(
                  child: Padding(
                padding: const EdgeInsets.all(15),
                child: Column(
                  children: [
                    Align(
                      alignment: Alignment.topLeft,
                      child: TextButton(
                        child: const Text("Clear"),
                        onPressed: () => _platform.invokeMethod('clearClients'),
                      ),
                    ),
                    Expanded(
                        child: ListView(
                      children: widget.clients
                          .map((client) => SwitchListTile(
                              title: Text(client.efb),
                              subtitle: Text(
                                  "${client.source} last seen: ${calcSince(client.lastDiscover)}"),
                              value: client.isEnabled,
                              onChanged: (bool value) => _platform.invokeMethod(
                                      'setEFBEnabled', {
                                    'client':
                                        jsonEncode(client.source.toJson()),
                                    'enabled': value
                                  })))
                          .toList(),
                    ))
                  ],
                ),
              )),
            )
          ],
        ));
  }
}

class LabelBox extends StatelessWidget {
  final String label;
  final Widget child;
  final TextStyle? labelStyle;

  const LabelBox(
      {Key? key, required this.label, required this.child, this.labelStyle})
      : super(key: key);

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: <Widget>[
        Text(label,
            style: labelStyle ?? Theme.of(context).textTheme.labelMedium),
        child
      ],
    );
  }
}
