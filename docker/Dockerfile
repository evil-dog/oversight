# OverSight build environment: Temurin JDK 17 (matches CI) + Android SDK.
# Base image comes from the ECR Public mirror of Docker Official Images to avoid Docker Hub rate limits.
#
# Build:  docker build -t oversight-build docker/
FROM public.ecr.aws/docker/library/eclipse-temurin:17-jdk-noble

ARG CMDLINE_TOOLS_VERSION=16111833
ARG COMPILE_SDK=android-37.0
ARG BUILD_TOOLS=36.0.0

ENV ANDROID_HOME=/opt/android-sdk
ENV ANDROID_SDK_ROOT=/opt/android-sdk
ENV PATH=${PATH}:/opt/android-sdk/cmdline-tools/latest/bin:/opt/android-sdk/platform-tools

RUN apt-get update \
    && apt-get install -y --no-install-recommends unzip git \
    && rm -rf /var/lib/apt/lists/*

RUN mkdir -p ${ANDROID_HOME}/cmdline-tools \
    && curl -fsSL -o /tmp/cmdline-tools.zip \
       https://dl.google.com/android/repository/commandlinetools-linux-${CMDLINE_TOOLS_VERSION}_latest.zip \
    && unzip -q /tmp/cmdline-tools.zip -d ${ANDROID_HOME}/cmdline-tools \
    && mv ${ANDROID_HOME}/cmdline-tools/cmdline-tools ${ANDROID_HOME}/cmdline-tools/latest \
    && rm /tmp/cmdline-tools.zip

RUN yes | sdkmanager --licenses > /dev/null \
    && sdkmanager --install \
       "platform-tools" \
       "platforms;${COMPILE_SDK}" \
       "build-tools;${BUILD_TOOLS}" \
    && rm -rf /root/.android

WORKDIR /project
