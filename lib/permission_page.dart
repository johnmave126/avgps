import 'package:avgps/utils.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

typedef PermissionRequestCallback = void Function(PermissionStatus);

class PermissionRequestWidget extends StatelessWidget {
  static const platform = MethodChannel('avgps.youmu.moe/rpc');

  final PermissionStatus permissionStatus;
  final PermissionRequestCallback setStatus;

  static const TITLES = [
    "", // Granted
    "Enable Location Service", // Location Unavailable
    "Allow Your Location", // Location Denied
    "Allow Background Access", // Background Location Denied
  ];

  static const DESCS = [
    "",
    // Granted
    "avGPS needs location service in order to access GPS data.",
    // Location Unavailable
    "avGPS wants to know your location in order to pass GPS location to EFBs and query satellite status.",
    // Location Denied
    "avGPS wants to know your location all the time in order to keep transferring GPS data when in background.",
    // Background Location Denied
  ];

  static const BTN = [
    "", // Granted
    "Turn On Location Services", // Location Unavailable
    "Allow Location Access", // Location Denied
    "Allow Background Access", // Background Location Denied
  ];

  static const CBS = [
    nop, // Granted
    openLocationSettings, // Location Unavailable
    requestLocationPermission, // Location Denied
    requestBackgroundLocationPermission, // Background Location Denied
  ];

  static void nop(PermissionRequestCallback callback) {}

  static void openLocationSettings(PermissionRequestCallback callback) async {
    await platform.invokeMethod('openLocationSettings');
    final int permissionIdx = await platform.invokeMethod('getPermissions');
    callback(PermissionStatus.values[permissionIdx]);
  }

  static void requestLocationPermission(
      PermissionRequestCallback callback) async {
    final int permissionIdx =
        await platform.invokeMethod('requestLocationPermission');
    callback(PermissionStatus.values[permissionIdx]);
  }

  static void requestBackgroundLocationPermission(
      PermissionRequestCallback callback) async {
    final int permissionIdx =
        await platform.invokeMethod('requestLocationBackgroundPermission');
    callback(PermissionStatus.values[permissionIdx]);
  }

  const PermissionRequestWidget(
      {Key? key, required this.permissionStatus, required this.setStatus})
      : super(key: key);

  @override
  Widget build(BuildContext context) {
    return Padding(
        padding: const EdgeInsets.all(40),
        child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: <Widget>[
              Text(
                TITLES[permissionStatus.index],
                style: Theme.of(context).textTheme.headlineMedium,
                textAlign: TextAlign.center,
              ),
              const SizedBox(height: 10),
              Flexible(
                  child: Text(DESCS[permissionStatus.index],
                      style: Theme.of(context).textTheme.bodyLarge,
                      textAlign: TextAlign.center)),
              const SizedBox(height: 20),
              ElevatedButton(
                  onPressed: () => CBS[permissionStatus.index](setStatus),
                  style: ElevatedButton.styleFrom(
                      minimumSize: const Size.fromHeight(50)),
                  child: Text(BTN[permissionStatus.index]))
            ]));
  }
}
