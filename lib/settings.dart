import 'package:avgps/utils.dart';
import 'package:awesome_select/awesome_select.dart';
import 'package:flutter/material.dart';
import 'package:package_info_plus/package_info_plus.dart';
import 'package:url_launcher/url_launcher.dart';

final List<S2Choice<AltitudeUnit>> altitudeUnitChoices = [
  S2Choice(value: AltitudeUnit.m, title: "meter"),
  S2Choice(value: AltitudeUnit.ft, title: "feet"),
];

final List<S2Choice<HorizontalUnit>> horizontalUnitChoices = [
  S2Choice(value: HorizontalUnit.m, title: "meter"),
  S2Choice(value: HorizontalUnit.ft, title: "feet"),
  S2Choice(value: HorizontalUnit.nm, title: "nautical miles"),
  S2Choice(value: HorizontalUnit.sm, title: "statute miles"),
];

final List<S2Choice<SpeedUnit>> speedUnitChoices = [
  S2Choice(value: SpeedUnit.mps, title: "m/s"),
  S2Choice(value: SpeedUnit.kmh, title: "km/h"),
  S2Choice(value: SpeedUnit.mph, title: "mph"),
  S2Choice(value: SpeedUnit.kt, title: "knot"),
];

final Uri _sourceCodeUrl = Uri.parse('https://github.com/johnmave126/avGPS');

String _leagalese =
    '''All information in the avGPS is provided "as is", with no guarantee of completeness, accuracy, timeliness or of the results obtained from the use of this information, and without warranty of any kind, express or implied, including, but not limited to warranties of performance, merchantability and fitness for a particular purpose.

The author will not be liable to You or anyone else for any decision made or action taken in reliance on the information given by the Service or for any consequential, special or similar damages, even if advised of the possibility of such damages.''';

class SettingsWidget extends StatelessWidget {
  final Settings settings;
  final void Function() onChange;
  final PackageInfo? packageInfo;
  final scrollController = ScrollController();

  SettingsWidget(
      {Key? key,
      required this.settings,
      this.packageInfo,
      required this.onChange})
      : super(key: key);

  @override
  Widget build(BuildContext context) {
    final widget = context.widget as SettingsWidget;

    return Scrollbar(
        controller: scrollController,
        child: ListView(controller: scrollController, children: <Widget>[
          Padding(
            padding: const EdgeInsets.fromLTRB(30, 30, 30, 5),
            child: Text(
              "Unit",
              style: Theme.of(context)
                  .textTheme
                  .labelMedium
                  ?.apply(color: Theme.of(context).dividerColor),
            ),
          ),
          Padding(
              padding: const EdgeInsets.symmetric(horizontal: 15, vertical: 5),
              child: SmartSelect<AltitudeUnit>.single(
                title: 'Altitude',
                selectedValue: widget.settings.altitudeUnit,
                choiceItems: altitudeUnitChoices,
                modalType: S2ModalType.bottomSheet,
                onChange: (selected) {
                  if (selected.value != null) {
                    settings.setAltitudeUnit(selected.value!);
                    onChange();
                  }
                },
              )),
          Padding(
              padding: const EdgeInsets.symmetric(horizontal: 15, vertical: 5),
              child: SmartSelect<SpeedUnit>.single(
                title: 'Speed',
                selectedValue: widget.settings.speedUnit,
                choiceItems: speedUnitChoices,
                modalType: S2ModalType.bottomSheet,
                onChange: (selected) {
                  if (selected.value != null) {
                    settings.setSpeedUnit(selected.value!);
                    onChange();
                  }
                },
              )),
          Padding(
              padding: const EdgeInsets.symmetric(horizontal: 15, vertical: 5),
              child: SmartSelect<HorizontalUnit>.single(
                title: 'Horizontal Accuracy',
                selectedValue: widget.settings.horizontalUnit,
                choiceItems: horizontalUnitChoices,
                modalType: S2ModalType.bottomSheet,
                onChange: (selected) {
                  if (selected.value != null) {
                    settings.setHorizontalUnit(selected.value!);
                    onChange();
                  }
                },
              )),
          const Divider(),
          Padding(
            padding: const EdgeInsets.fromLTRB(30, 30, 30, 5),
            child: Text(
              "About",
              style: Theme.of(context)
                  .textTheme
                  .labelMedium
                  ?.apply(color: Theme.of(context).dividerColor),
            ),
          ),
          Padding(
              padding: const EdgeInsets.symmetric(horizontal: 15, vertical: 5),
              child: ListTile(
                title: const Text("About"),
                onTap: () => showAboutDialog(
                    context: context,
                    applicationName: widget.packageInfo?.appName ?? "",
                    applicationVersion: widget.packageInfo?.version ?? "",
                    applicationLegalese: _leagalese),
              )),
          Padding(
              padding: const EdgeInsets.symmetric(horizontal: 15, vertical: 5),
              child: ListTile(
                title: const Text("Source Code"),
                onTap: () => _launchUrl(),
              )),
        ]));
  }
}

void _launchUrl() async {
  // TODO: does not seem to work
  if (!await launchUrl(_sourceCodeUrl, mode: LaunchMode.externalApplication)) {
    throw 'Could not launch $_sourceCodeUrl';
  }
}
