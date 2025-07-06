# Setup Guide

This document explains how to configure the project on a new machine.

## Clone the repository

```bash
git clone https://github.com/your-user/CiceroAutoPost.git
cd CiceroAutoPost
```

Initialize the submodules so that `instagram4j` is available:

```bash
git submodule update --init --recursive
```

## Environment

Create a `.env` file in the root directory:

```bash
OPENAI_API_KEY=sk-...
```

## Build the app

Use the Gradle wrapper to compile the debug APK:

```bash
cd socialtools_app
./gradlew assembleDebug
```

The wrapper script will automatically download the required Gradle files if they are not present.
