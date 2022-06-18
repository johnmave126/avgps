import 'dart:async';
import 'dart:convert';

import 'package:avgps/gps_status.dart';
import 'package:avgps/home.dart';
import 'package:avgps/permission_page.dart';
import 'package:avgps/settings.dart';
import 'package:avgps/utils.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:package_info_plus/package_info_plus.dart';

void main() {
  runApp(const MyApp());
}

class MyApp extends StatelessWidget {
  const MyApp({Key? key}) : super(key: key);

  // This widget is the root of your application.
  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'GPS for Aviation',
      theme: ThemeData(
        // This is the theme of your application.
        colorSchemeSeed: const Color(0xFF12525E),
        scaffoldBackgroundColor: const Color(0xFFEFEFEF),
      ),
      home: const MyMainWidget(),
    );
  }
}

class MyMainWidget extends StatefulWidget {
  const MyMainWidget({Key? key}) : super(key: key);

  @override
  State<MyMainWidget> createState() => _MyMainWidgetState();
}

class _MyMainWidgetState extends State<MyMainWidget>
    with WidgetsBindingObserver {
  final _platform = const MethodChannel('avgps.youmu.moe/rpc');
  final _gnssChannel = const EventChannel('avgps.youmu.moe/gnss');
  final _locationChannel = const EventChannel('avgps.youmu.moe/location');
  final _clientChannel = const EventChannel('avgps.youmu.moe/client');

  StreamSubscription? _locationSubscription;
  StreamSubscription? _clientSubscription;
  StreamSubscription? _gnssSubscription;
  LocationData? _location;
  List<Client> _clients = [];
  List<SatelliteData> _satellites = [];

  PackageInfo? _packageInfo;
  Settings _settings = Settings();

  int _selectedIndex = 0;
  PermissionStatus _permissionStatus = PermissionStatus.locationUnavailable;

  void _onItemTapped(int index) {
    setState(() {
      _selectedIndex = index;
    });
  }

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _getPermission();
    _initClients();
    _initLocation();
    _getPreferences();
    _initPackageInfo();
  }

  @override
  void dispose() {
    super.dispose();
    WidgetsBinding.instance.removeObserver(this);
    _cancelSubscription();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) {
      _getPermission();
      _initClients();
      _initLocation();
    } else {
      _cancelSubscription();
    }
  }

  void _startSubscription() {
    _locationSubscription ??=
        _locationChannel.receiveBroadcastStream().listen((message) {
      Map<String, dynamic> locationMap = jsonDecode(message as String);
      LocationData location = LocationData.fromJson(locationMap);
      setState(() {
        _location = location;
      });
    }, cancelOnError: true);

    _clientSubscription ??=
        _clientChannel.receiveBroadcastStream().listen((message) {
      final clientMapList = jsonDecode(message as String) as List<dynamic>;
      List<Client> clients = clientMapList
          .map(
              (clientMap) => Client.fromJson(clientMap as Map<String, dynamic>))
          .toList();
      setState(() {
        _clients = clients;
      });
    }, cancelOnError: true);

    _gnssSubscription ??=
        _gnssChannel.receiveBroadcastStream().listen((message) {
      final satellitesMapList = jsonDecode(message as String) as List<dynamic>;
      List<SatelliteData> satellites = satellitesMapList
          .map((satelliteMap) =>
              SatelliteData.fromJson(satelliteMap as Map<String, dynamic>))
          .toList();
      setState(() {
        _satellites = satellites;
      });
    }, cancelOnError: true);
  }

  void _cancelSubscription() {
    _locationSubscription?.cancel();
    _locationSubscription = null;
    _clientSubscription?.cancel();
    _clientSubscription = null;
    _gnssSubscription?.cancel();
    _gnssSubscription = null;
  }

  void _initClients() async {
    final message = await _platform.invokeMethod('getClients') as String;
    final clientMapList = jsonDecode(message) as List<dynamic>;
    List<Client> clients = clientMapList
        .map((clientMap) => Client.fromJson(clientMap as Map<String, dynamic>))
        .toList();
    setState(() {
      _clients = clients;
    });
  }

  void _initLocation() async {
    final message = await _platform.invokeMethod('getLocation') as String;
    final locationMap = jsonDecode(message) as Map<String, dynamic>?;
    if (locationMap != null) {
      LocationData location = LocationData.fromJson(locationMap);
      setState(() {
        _location = location;
      });
    }
  }

  void _initPackageInfo() async {
    PackageInfo packageInfo = await PackageInfo.fromPlatform();
    setState(() {
      _packageInfo = packageInfo;
    });
  }

  void _getPreferences() async {
    final settings = await Settings.retrieve();

    setState(() {
      _settings = settings;
    });
  }

  void _getPermission() async {
    final int permission = await _platform.invokeMethod('getPermissions');

    if (permission == PermissionStatus.granted.index) {
      _startSubscription();
    }
    setState(() {
      _permissionStatus = PermissionStatus.values[permission];
    });
  }

  Widget _getSubview() {
    switch (_selectedIndex) {
      case 0:
        return HomeWidget(
            location: _location, clients: _clients, settings: _settings);
      case 1:
        return GPSStatusWidget(satellites: _satellites);
      case 2:
        return SettingsWidget(
          settings: _settings,
          packageInfo: _packageInfo,
          onChange: _getPreferences,
        );
    }
    throw UnimplementedError("Invalid selected index");
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        // Here we take the value from the MyHomePage object that was created by
        // the App.build method, and use it to set our appbar title.
        title: const Text("GPS for Aviation"),
      ),
      body: Center(
        // Center is a layout widget. It takes a single child and positions it
        // in the middle of the parent.
        child: _permissionStatus == PermissionStatus.granted
            ? _getSubview()
            : PermissionRequestWidget(
                permissionStatus: _permissionStatus,
                setStatus: (newPermission) {
                  if (newPermission == PermissionStatus.granted) {
                    _startSubscription();
                  }
                  setState(() {
                    _permissionStatus = newPermission;
                  });
                }),
      ),
      bottomNavigationBar: BottomNavigationBar(
        items: const <BottomNavigationBarItem>[
          BottomNavigationBarItem(
            icon: Icon(Icons.my_location),
            label: 'Location',
          ),
          BottomNavigationBarItem(
              icon: Icon(Icons.satellite_alt), label: 'Satellites'),
          BottomNavigationBarItem(icon: Icon(Icons.settings), label: 'Settings')
        ],
        currentIndex: _selectedIndex,
        selectedItemColor: const Color(0xFFD27AD1),
        onTap: _onItemTapped,
      ),
    );
  }
}
