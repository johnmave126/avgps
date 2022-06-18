import 'package:avgps/utils.dart';
import 'package:collection/collection.dart';
import 'package:flutter/material.dart';
import 'package:flutter_sticky_header/flutter_sticky_header.dart';

class GPSStatusWidget extends StatelessWidget {
  final List<SatelliteData> satellites;

  const GPSStatusWidget({Key? key, required this.satellites}) : super(key: key);

  @override
  Widget build(BuildContext context) {
    var satellitesByType = satellites.groupListsBy((element) => element.type);
    satellitesByType.forEach((key, value) {
      value.sort((a, b) => a.id.compareTo(b.id));
    });
    return CustomScrollView(
      slivers: [
        "GPS",
        "Beidou",
        "Galileo",
        "GLONASS",
        "Michibiki",
        "IRNSS",
        "SBAS",
        "Unknown"
      ]
          .where((type) => satellitesByType.containsKey(type))
          .map((type) => SliverStickyHeader(
              header: Container(
                  color: Colors.blueGrey,
                  padding: const EdgeInsets.fromLTRB(15, 10, 15, 5),
                  child: Column(children: [
                    Align(
                        alignment: Alignment.centerLeft,
                        child: Text(type,
                            style: Theme.of(context)
                                .textTheme
                                .titleLarge
                                ?.apply(color: Colors.white))),
                    const SizedBox(height: 10),
                    Row(
                      crossAxisAlignment: CrossAxisAlignment.center,
                      children: [
                        const Expanded(flex: 1, child: SizedBox()),
                        Expanded(
                            flex: 2,
                            child: Align(
                                alignment: Alignment.center,
                                child: Text("ID",
                                    style: Theme.of(context)
                                        .textTheme
                                        .bodyMedium
                                        ?.apply(
                                            color: Colors.white,
                                            fontWeightDelta: 1)))),
                        Expanded(
                            flex: 4,
                            child: Align(
                                alignment: Alignment.center,
                                child: Text("CNR",
                                    style: Theme.of(context)
                                        .textTheme
                                        .bodyMedium
                                        ?.apply(
                                            color: Colors.white,
                                            fontWeightDelta: 1)))),
                        Expanded(
                            flex: 4,
                            child: Align(
                                alignment: Alignment.center,
                                child: Text("Azimuth",
                                    style: Theme.of(context)
                                        .textTheme
                                        .bodyMedium
                                        ?.apply(
                                            color: Colors.white,
                                            fontWeightDelta: 1)))),
                        Expanded(
                            flex: 4,
                            child: Align(
                                alignment: Alignment.center,
                                child: Text("Elevation",
                                    style: Theme.of(context)
                                        .textTheme
                                        .bodyMedium
                                        ?.apply(
                                            color: Colors.white,
                                            fontWeightDelta: 1)))),
                      ],
                    )
                  ])),
              sliver: SliverList(
                  delegate: SliverChildBuilderDelegate((context, index) {
                final satellite = satellitesByType[type]![index];
                return Container(
                    padding:
                        const EdgeInsets.symmetric(horizontal: 15, vertical: 5),
                    width: double.infinity,
                    child: Row(
                        crossAxisAlignment: CrossAxisAlignment.center,
                        children: [
                          Expanded(
                              flex: 1,
                              child: Align(
                                  alignment: Alignment.center,
                                  child: Icon(Icons.fiber_manual_record,
                                      color: satellite.used
                                          ? Colors.green
                                          : Colors.grey,
                                      size: 10))),
                          Expanded(
                              flex: 2,
                              child: Align(
                                  alignment: Alignment.center,
                                  child: Text(satellite.id.toString()))),
                          Expanded(
                              flex: 4,
                              child: Align(
                                  alignment: Alignment.center,
                                  child:
                                      Text(satellite.cnr.toStringAsFixed(1)))),
                          Expanded(
                              flex: 4,
                              child: Align(
                                  alignment: Alignment.center,
                                  child: Text(
                                      "${satellite.azimuth.toStringAsFixed(1)}°"))),
                          Expanded(
                              flex: 4,
                              child: Align(
                                  alignment: Alignment.center,
                                  child: Text(
                                      "${satellite.elevation.toStringAsFixed(1)}°"))),
                        ]));
              }, childCount: satellitesByType[type]!.length))))
          .toList(),
    );
  }
}
