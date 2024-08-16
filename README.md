<p align="center">
  <a href='http://measure.sh'><img alt="measurelogo" src="https://github.com/user-attachments/assets/6d6b161d-653a-4027-83e2-f749140d13d6"></a>
</p>
<p align="center">
  <a href='http://makeapullrequest.com'><img alt='PRs Welcome' src='https://img.shields.io/badge/PRs-welcome-brightgreen.svg?style=shields'/></a>
  <img alt="GitHub commit activity" src="https://img.shields.io/github/commit-activity/m/measure-sh/measure"/>
  <img alt="GitHub closed issues" src="https://img.shields.io/github/issues-closed/measure-sh/measure"/>
</p>

## Measure is an open source tool to monitor mobile apps

- Capture Crashes and ANRs automatically
- Monitor app health metrics such as launch times, crash rates and app sizes 
- Get screenshots with exception reports
- View full event timelines of error sessions with auto-tracked user clicks, navigation events, http calls, cpu usage, memory usage and more for deeper context
- Track custom events with additional business specific attributes
- Self hosted and private. Your data stays in your servers


## Table of Contents

- [Measure is an open source tool to monitor mobile apps](#measure-is-an-open-source-tool-to-monitor-mobile-apps)
- [Table of Contents](#table-of-contents)
- [Quick start](#quick-start)
- [Features](#features)
  - [User Journeys](#user-journeys)
  - [App Health](#app-health)
  - [Crashes and ANRs](#crashes-and-anrs)
  - [Session Timelines](#session-timelines)
- [Docs](#docs)
- [Platform Support](#platform-support)
- [Philosophy](#philosophy)
- [Roadmap](#roadmap)
- [Open Source](#open-source)

## Quick start

Measure is available as a self hosted platform that comes with a simple one line install script. Check out our [Self hosting](./docs/hosting/README.md) and [Android SDK](./docs/android/README.md) guides.

## Features

### User Journeys

Understand how users move through your app. Easily visualise screens most affected by issues.

<p align="center">
  <img width="800" alt="Measure User Journey" src="https://github.com/user-attachments/assets/119dc35d-0c75-46a5-9cb5-2eb2551e2888">
</p>

### App Health

Monitor important metrics to stay on top of app health. Quickly see deltas to make sure you're moving in the right direction.

<p align="center">
  <img width="800" alt="Measure App Health" src="https://github.com/user-attachments/assets/7bffe005-54cd-4b44-a639-d2910edda211">
</p>

### Crashes and ANRs

Automatically track Crashes and ANRs. Dive deeper with screenshots, filters and detailed stacktraces

<p align="center">
  <img width="800" alt="Measure Crashes" src="https://github.com/user-attachments/assets/68125854-ff00-41c9-a795-3bff51a185aa">
</p>

### Session Timelines

Debug issues easily with full session timelines. Get the complete context with automatic tracking for clicks, navigations, http calls and more.

<p align="center">
  <img width="800" alt="Measure Sessions Timelines" src="https://github.com/user-attachments/assets/59179612-58bb-4935-a740-51de1f787e37">
</p>

## Docs

1. [**Self Hosting Guide**](./docs/hosting/README.md) - Get started with hosting Measure
2. [**Android SDK Guide**](./android/README.md) - Integrate our Android SDK and start measuring in no time
3. [**REST API Docs**](./docs/api/README.md) - REST APIs used by the dashboard app and SDKs
4. [**Versioning Guide**](./docs/versioning/README.md) - Understand how versions are tagged
5. [**Contribution Guide**](./docs/contributing/CONTRIBUTING.md) - Contribute to Measure


## Platform Support

Currently, we support Android with iOS, Flutter, React Native and Unity SDKs planned for the future.

## Philosophy

Our mission is to build the best tool for monitoring mobile apps. 

Building and maintaining mobile apps in production is hard and developers need to use a variety of different tools to collect issues, measure performance and analyse user behavior without having any control over where, how and for how long their data is stored. 

We aim to build a tool that unifies and simplifies monitoring capablitlies for mobile teams while providing full control over 
the collection and storage of data.

We operate in public as much as possible and we aim to be community focused and driven by feedback from real developers building in the trenches.

We would love for you to contribute to Measure by opening issues, sending PRs and recommending us to your friends! 

## Roadmap

Check out what's being worked on and what's in the pipeline in our [Roadmap](https://github.com/orgs/measure-sh/projects/5/views/1)

## Open Source

Measure is fully open source and is available under the [Apache 2.0 license](./LICENSE)