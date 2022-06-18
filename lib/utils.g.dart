// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'utils.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

LocationData _$LocationDataFromJson(Map<String, dynamic> json) => LocationData(
      const _DateTimeEpochConverter().fromJson(json['timestamp'] as int),
      (json['longitude'] as num).toDouble(),
      (json['latitude'] as num).toDouble(),
      (json['altitude'] as num).toDouble(),
      (json['speed'] as num).toDouble(),
      (json['bearing'] as num).toDouble(),
      (json['horizontalAccuracy'] as num).toDouble(),
      (json['verticalAccuracy'] as num).toDouble(),
    );

Map<String, dynamic> _$LocationDataToJson(LocationData instance) =>
    <String, dynamic>{
      'timestamp': const _DateTimeEpochConverter().toJson(instance.timestamp),
      'longitude': instance.longitude,
      'latitude': instance.latitude,
      'altitude': instance.altitude,
      'speed': instance.speed,
      'bearing': instance.bearing,
      'horizontalAccuracy': instance.horizontalAccuracy,
      'verticalAccuracy': instance.verticalAccuracy,
    };

InetSocketAddress _$InetSocketAddressFromJson(Map<String, dynamic> json) =>
    InetSocketAddress(
      json['address'] as String,
      json['port'] as int,
    );

Map<String, dynamic> _$InetSocketAddressToJson(InetSocketAddress instance) =>
    <String, dynamic>{
      'address': instance.address,
      'port': instance.port,
    };

Client _$ClientFromJson(Map<String, dynamic> json) => Client(
      InetSocketAddress.fromJson(json['source'] as Map<String, dynamic>),
      json['efb'] as String,
      const _DateTimeEpochConverter().fromJson(json['lastDiscover'] as int),
      json['isEnabled'] as bool,
    );

Map<String, dynamic> _$ClientToJson(Client instance) => <String, dynamic>{
      'source': instance.source,
      'efb': instance.efb,
      'lastDiscover':
          const _DateTimeEpochConverter().toJson(instance.lastDiscover),
      'isEnabled': instance.isEnabled,
    };

SatelliteData _$SatelliteDataFromJson(Map<String, dynamic> json) =>
    SatelliteData(
      json['type'] as String,
      json['id'] as int,
      (json['azimuth'] as num).toDouble(),
      (json['elevation'] as num).toDouble(),
      (json['cnr'] as num).toDouble(),
      json['used'] as bool,
    );

Map<String, dynamic> _$SatelliteDataToJson(SatelliteData instance) =>
    <String, dynamic>{
      'type': instance.type,
      'id': instance.id,
      'azimuth': instance.azimuth,
      'elevation': instance.elevation,
      'cnr': instance.cnr,
      'used': instance.used,
    };
