import 'package:json_annotation/json_annotation.dart';
import 'package:shared_preferences/shared_preferences.dart';

part 'utils.g.dart';

enum PermissionStatus {
  granted,
  locationUnavailable,
  locationDenied,
  backgroundLocationDenied,
}

enum AltitudeUnit { m, ft }

String altitudeIn(double x, AltitudeUnit unit) {
  double y;
  switch (unit) {
    case AltitudeUnit.m:
      y = x;
      return "${y.round()} m";
    case AltitudeUnit.ft:
      y = x / 0.3048;
      return "${y.round()} ft";
  }
}

enum HorizontalUnit { m, ft, nm, sm }

String horizontalIn(double x, HorizontalUnit unit) {
  double y;
  switch (unit) {
    case HorizontalUnit.m:
      y = x;
      return "${y.round()} m";
    case HorizontalUnit.ft:
      y = x / 0.3048;
      return "${y.round()} ft";
    case HorizontalUnit.nm:
      y = x / 1852;
      return "${y.toStringAsFixed(2)} nm";
    case HorizontalUnit.sm:
      y = x / 1609.344;
      return "${y.toStringAsFixed(2)} miles";
  }
}

enum SpeedUnit { mps, kmh, mph, kt }

String speedIn(double x, SpeedUnit unit) {
  double y;
  switch (unit) {
    case SpeedUnit.mps:
      y = x;
      return "${y.toStringAsFixed(1)} m/s";
    case SpeedUnit.kmh:
      y = x * 3.6;
      return "${y.toStringAsFixed(1)} km/h";
    case SpeedUnit.mph:
      y = x * 2.23694;
      return "${y.toStringAsFixed(1)} mph";
    case SpeedUnit.kt:
      y = x * 1.94384;
      return "${y.toStringAsFixed(1)} knot";
  }
}

class Settings {
  AltitudeUnit altitudeUnit;
  HorizontalUnit horizontalUnit;
  SpeedUnit speedUnit;

  Settings(
      {this.altitudeUnit = AltitudeUnit.m,
      this.horizontalUnit = HorizontalUnit.m,
      this.speedUnit = SpeedUnit.mps});

  static Future<Settings> retrieve() async {
    final prefs = await SharedPreferences.getInstance();
    final altitudeUnit =
        AltitudeUnit.values[prefs.getInt('altitude_unit') ?? 0];
    final horizontalUnit =
        HorizontalUnit.values[prefs.getInt('horizontal_unit') ?? 0];
    final speedUnit = SpeedUnit.values[prefs.getInt('speed_unit') ?? 0];

    return Settings(
        altitudeUnit: altitudeUnit,
        horizontalUnit: horizontalUnit,
        speedUnit: speedUnit);
  }

  void setAltitudeUnit(AltitudeUnit unit) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setInt('altitude_unit', unit.index);
  }

  void setHorizontalUnit(HorizontalUnit unit) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setInt('horizontal_unit', unit.index);
  }

  void setSpeedUnit(SpeedUnit unit) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setInt('speed_unit', unit.index);
  }
}

class _DateTimeEpochConverter implements JsonConverter<DateTime, int> {
  const _DateTimeEpochConverter();

  @override
  DateTime fromJson(int json) => DateTime.fromMillisecondsSinceEpoch(json);

  @override
  int toJson(DateTime object) => object.millisecondsSinceEpoch;
}

@JsonSerializable()
@_DateTimeEpochConverter()
class LocationData {
  final DateTime timestamp;
  final double longitude;
  final double latitude;
  final double altitude;
  final double speed;
  final double bearing;
  final double horizontalAccuracy;
  final double verticalAccuracy;

  LocationData(this.timestamp, this.longitude, this.latitude, this.altitude,
      this.speed, this.bearing, this.horizontalAccuracy, this.verticalAccuracy);

  String formatCoord() {
    return "${formatDegreeDecimal(latitude, [
          "S",
          "N"
        ])}\n${formatDegreeDecimal(longitude, ["W", "E"])}";
  }

  static String formatDegreeDecimal(double deg, List<String> suffix) {
    String suf = deg < 0 ? suffix[0] : suffix[1];
    int d = deg.truncate();
    double md = (deg - d).abs() * 60;
    int m = md.truncate();
    double s = (md - m) * 60;
    return "${d.abs().toString().padLeft(3, '0')}° ${m.toString().padLeft(2, '0')}′ ${s.toStringAsFixed(3).padLeft(6, '0')} $suf";
  }

  factory LocationData.fromJson(Map<String, dynamic> json) =>
      _$LocationDataFromJson(json);

  Map<String, dynamic> toJson() => _$LocationDataToJson(this);
}

@JsonSerializable()
class InetSocketAddress {
  final String address;
  final int port;

  InetSocketAddress(this.address, this.port);

  @override
  String toString() => "$address:$port";

  factory InetSocketAddress.fromJson(Map<String, dynamic> json) =>
      _$InetSocketAddressFromJson(json);

  Map<String, dynamic> toJson() => _$InetSocketAddressToJson(this);
}

@JsonSerializable()
@_DateTimeEpochConverter()
class Client {
  final InetSocketAddress source;
  final String efb;
  final DateTime lastDiscover;
  bool isEnabled;

  Client(this.source, this.efb, this.lastDiscover, this.isEnabled);

  factory Client.fromJson(Map<String, dynamic> json) => _$ClientFromJson(json);

  Map<String, dynamic> toJson() => _$ClientToJson(this);
}

@JsonSerializable()
class SatelliteData {
  final String type;
  final int id;
  final double azimuth;
  final double elevation;
  final double cnr;
  final bool used;

  SatelliteData(
      this.type, this.id, this.azimuth, this.elevation, this.cnr, this.used);

  factory SatelliteData.fromJson(Map<String, dynamic> json) =>
      _$SatelliteDataFromJson(json);

  Map<String, dynamic> toJson() => _$SatelliteDataToJson(this);
}
