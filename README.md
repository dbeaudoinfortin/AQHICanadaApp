# Canada AQHI Android App <img src="https://github.com/user-attachments/assets/2aa9f5e2-b4b7-4dc5-a3df-0d04fb6171a7" height="35"/>

A no-nonsense Android app & widgets for displaying Canadian Air Quality Health Index (AQHI) conditions, alerts, forecasts and historical readings.
<p align="center">&nbsp;</p>
<p align="center">
<a href="https://github.com/dbeaudoinfortin/AQHICanadaApp/releases/latest"><b>Download and install the latest release.</b></a>
</p>

Features:
- View the latest air quality readings from the nearest monitoring station to your location.
- See AQHI forecasts for the next several days.
- Receive alerts when ECCC issues warnings for your selected area.
- Explore daily and hourly trends, including median “typical” values based on a 10-year analysis of air quality data.
- Choose from three configurable home screen widgets to monitor AQHI at a glance.
- Automatically switches between light and dark mode based on your system settings.
- Maps are entirely offline, with no location data sent to any server.

## Main App
<p align="center">
  <img src="https://github.com/user-attachments/assets/525d4af8-b17d-491a-8696-4072c5be20ea" width="400" />
</p>

<p align="center">
  <img src="https://github.com/user-attachments/assets/19d1a29c-6dbe-410e-b981-72da76b51d73" width="400" />
  <img src="https://github.com/user-attachments/assets/9ee88b49-10e0-45fe-89f5-d128f8934a15" width="400" />
  <img src="https://github.com/user-attachments/assets/159ce2f9-c024-4efe-b952-3ecdc3d397ea" width="400" />
  <img src="https://github.com/user-attachments/assets/19365e74-b855-40aa-99f3-fbe53e3ecc8c" width="400" />
</p>

<p align="center">
  <img src="https://github.com/user-attachments/assets/0c14c8de-2ecb-4d81-b82e-a9a400ea8476" width="400" />
  <img src="https://github.com/user-attachments/assets/7dc414ad-5137-485f-bb24-67abbf16ba4d" width="400" />
  <img src="https://github.com/user-attachments/assets/debaab1a-7bc8-4684-ab10-60d5af95e0b4" width="400" />
  <img src="https://github.com/user-attachments/assets/5269e8aa-da67-4873-b35d-1a26d82c382a" width="400" />
  <img src="https://github.com/user-attachments/assets/0e9f578d-5913-42bc-a7e3-3da967473de8" width="400" />
  <img src="https://github.com/user-attachments/assets/05bc3a03-47d0-44c9-9919-a144107956df" width="400" />
  <img src="https://github.com/user-attachments/assets/f3c5fd2f-a322-4d87-883b-18c8f0aaff39" width="400" />
  <img src="https://github.com/user-attachments/assets/8b9d6ade-25b5-41c0-b5f3-6a3989d3d897" width="400" />
</p>

> [!NOTE]  
> Both light mode and dark mode are supported and will switch automatically with the system preference.

> [!TIP]
> Tapping on the heat maps will reveal the raw data values!

## Widgets

Three widgets are provided: a small widget that displays the most recent AQHI reading at your location, a larger, wider widget that shows the AQHI reading on a relative scale, and a fun emoji smiley face widget that changes with the current AQHI reading.

<p align="center">
  <img src="https://github.com/user-attachments/assets/a27f71ca-6110-43d9-9bfe-3b1914a128e6" />
  <img src="https://github.com/user-attachments/assets/8b34092d-ea5a-4c4e-b08a-ac57d15c4eee" />
</p>

<p align="center">
  <img src="https://github.com/user-attachments/assets/7e22940d-4ab8-4e21-9f03-9f397eb8dbc3" width="400"/>
  <img src="https://github.com/user-attachments/assets/742c6ad0-ecdb-46c8-81be-d08509e88693" width="400"/>
</p>

<p align="center">
  <img src="https://github.com/user-attachments/assets/68e678ff-03c1-4b07-af1e-4254791a7d1e" width="300"/>
  <img src="https://github.com/user-attachments/assets/7a6e63f1-3756-4e72-97f0-a00d0e358937" width="300"/>
  <img src="https://github.com/user-attachments/assets/890a0a9e-a0e7-4019-9030-b5bbb5af89ce" width="300"/>
</p>

Widgets are configurable: you can set the background transparency as well as the light/dark mode. A preview of the widget is shown as you configure it.

<p align="center">
  <img src="https://github.com/user-attachments/assets/e3b143b1-a41f-4182-b6f3-8d8c42e5924f" width="400"/>
  <img src="https://github.com/user-attachments/assets/1d7829cb-10e0-4435-bdda-74062d95af6e" width="400"/>
</p>

> [!TIP]
> Setting light/dark mode to automatic will make the widget switch automatically with the system preference.

## Alerts

Alerts are displayed in the main app and in the large widget whenever Environment and Climate Change Canada (ECCC) issues a public air quality alert for your currently selected station. These are colour coded based on ECCC's severity scale: Warning (red), Watch (yellow), and Statement (grey).

<p align="center">
  <img src="https://github.com/user-attachments/assets/f09bc074-84dc-412a-83f0-66d2492f0051" width="400"/>
  <img src="https://github.com/user-attachments/assets/a6f859bb-5b30-4d9c-8834-ae80ee367e3c" width="400"/>
  <img src="https://github.com/user-attachments/assets/e1764e0f-27b6-4eb8-ba04-a43dc520ca4a" width="400"/>
</p>

## How it works
AQHI Data is pulled from Environment and Climate Change Canada's public API using the closest active monitoring station to your current location. The station definitions, current location, and current AQHI readings are all cached to prevent excessive calls to the API. Data is shared between the main app and the widgets.

The rendering of heat maps is entirely written from scratch by myself. My [GitHub project](https://github.com/dbeaudoinfortin/HeatMaps) contains rendering libraries for both Android and Java2D. Check it out if you want to add beautiful, customizable heat maps to you own Java project.

## About AQHI
The Canadian Air Quality Health Index (AQHI) is a made-in-Canada scale developed by Environment and Climate Change Canada (ECCC) to provide real-time information about the health risks associated with air pollution. It combines measurements from ground-level ozone, fine particulate matter (PM2.5), and nitrogen dioxide into a single numerical value on a scale from 1 (low risk) to 10 (high risk). All values above 10 are assigned a reading of 11+, indicating a very high risk.

**Scale:**
- 1-3 Low health risk
- 4-6 Moderate health risk
- 7-10 High health risk
- 11+  Very high health risk

As per ECCC, the calculation of the AQHI may change over time to reflect new understanding associated with air pollution health effects. AQHI values are calculated automatically using observations from a network of air quality monitoring stations across Canada. These measurements are collected in real-time and are not verified prior to publication. Forecast and historical AQHI values are reported on an hourly basis.

Realtime, historical and forecast AQHI data is provided by the [MSC GeoMet OpenAPI](https://api.weather.gc.ca/) service of the Meteorological Service of Canada (MSC) branch of Environment and Climate Change Canada (ECCC), a department of the Government of Canada. The data is licensed under the terms of the [Environment and Climate Change Canada Data Servers End-use Licence](https://eccc-msc.github.io/open-data/licence/readme_en/) and the [Canadian Open Government Licence](https://open.canada.ca/en/open-government-licence-canada).

For more information about the AQHI, visit [ECCC's website](https://www.canada.ca/en/environment-climate-change/services/air-quality-health-index/about.html).

If you are interested in the analysis of Canadian air quality data that has been validated, check out my [NAPS Data Analysis Project](https://github.com/dbeaudoinfortin/NAPSDataAnalysis) on GitHub.</p>

## Typical AQHI

The typical AQHI is calculated as the median hourly AQHI for the current hour of the day and current week of the year, based on data from your selected location. This calculation uses a 10-year period from 2014 to 2023, where at least 5 years of data are available for that location.

These values were generated using my [NAPS Data Analysis Toolbox](https://github.com/dbeaudoinfortin/NAPSDataAnalysis), which sources raw data from the [National Air Pollution Surveillance (NAPS)](https://data-donnees.az.ec.gc.ca/data/air/monitor/national-air-pollution-surveillance-naps-program/) program, part of Environment and Climate Change Canada. I calculated the tables for the median AQHI from the raw concentration readings of the 3 constituent pollutants (O3, NO2, & PM2.5) for all roughly 300 current active NAPS sites. These sites are then matched to the correct GeoMet stations using a bit of fuzzy logic with the latitude and longitude coordinates that both data sets provide. The formula for calculating AQHI is the following:

![AQHI Formula](https://github.com/user-attachments/assets/c5da4175-0a3b-4902-8c6d-15b99fc6a67c)

Typical AQHI data is provided by David Fortin under the terms of the [MIT License](https://github.com/dbeaudoinfortin/AQHICanadaApp?tab=MIT-1-ov-file).

## Map Data

The map used for viewing and selecting locations is stored entirely on-device, as I do not want to send your location off-device. The base map data comes from [CBCT3978](https://maps-cartes.services.geo.ca/server2_serveur2/rest/services/BaseMaps/CBCT3978/MapServer), which is freely provided by Natural Resources Canada under the terms of the [Canadian Open Government Licence](https://open.canada.ca/en/open-government-licence-canada). 

The map uses the Lambert Conformal Conic projection, which minimizes distortion across Canada. I cleaned up visible JPEG compression artifacts using a neural filter, then manually stitched together the tiles into a single 1-gigapixel image (35,000 × 30,000 pixels).

This high-resolution map was then tiled into 8 zoom levels and compressed in WebP format. The result is a folder structure containing approximately 21,500 files, taking up only about 160MB of space. The map is diplayed in the app using [Pierre Laurence's MapView library](https://github.com/p-lr/MapView). Check out his project!

## Requirements
- This app was developed for and tested on Android 14. 

## Privacy Statement
This app is made by David Fortin, an individual - not a corporation. It does not collect or share any of your personal information or data.

Your device's location is used only to identify the closest air quality monitoring station, and only if you explicitly grant the app permission to use location services. Your location information stays exclusively on your device and is never transmitted or shared.
 
This app determines your nearest air quality monitoring station by comparing your location (locally, on your device) with a list of stations obtained from Environment and Climate Change Canada's (ECCC) GeoMet service. Your location data is never sent to ECCC or any other server.

You can learn more about ECCC's GeoMet service [here](https://eccc-msc.github.io/open-data/).


## Legal Stuff

Copyright (c) 2024 David Fortin

This software is provided by David Fortin under the terms of the MIT License, meaning you are free to use it however you want, as long as you include the original copyright notice (above) and license notice in any copy you make. You just can't hold me liable in case something goes wrong. License details can be read [here](https://github.com/dbeaudoinfortin/AQHICanadaApp?tab=MIT-1-ov-file).

Realtime, historical and forecast AQHI data is provided by the [MSC GeoMet OpenAPI](https://api.weather.gc.ca/) service of the Meteorological Service of Canada (MSC) branch of Environment and Climate Change Canada (ECCC), a department of the Government of Canada. The data is licensed under the terms of the [Environment and Climate Change Canada Data Servers End-use Licence](https://eccc-msc.github.io/open-data/licence/readme_en/) and the [Canadian Open Government Licence](https://open.canada.ca/en/open-government-licence-canada).

Map data is provided by Natural Resources Canada, a department of the Government of Canada, also licensed under the terms of the [Canadian Open Government Licence](https://open.canada.ca/en/open-government-licence-canada).

Typical AQHI data is provided by David Fortin under the terms of the [MIT License](https://github.com/dbeaudoinfortin/AQHICanadaApp?tab=MIT-1-ov-file).
