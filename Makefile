.PHONY: debug release clean setup-sdk

# Android SDK configuration
SDK_VERSION := 11076708
SDK_URL := https://dl.google.com/android/repository/commandlinetools-linux-$(SDK_VERSION)_latest.zip
SDK_DIR := $(PWD)/build/.deps/android-sdk
SDK_TOOLS_ZIP := $(PWD)/build/.deps/commandlinetools.zip

# Create necessary directories
$(PWD)/build/.deps:
	mkdir -p $(PWD)/build/.deps

# Download and setup Android SDK
setup-sdk: $(PWD)/build/.deps
	@if [ ! -d "$(SDK_DIR)/cmdline-tools" ]; then \
		echo "Downloading Android SDK command-line tools..."; \
		curl -L -o $(SDK_TOOLS_ZIP) $(SDK_URL); \
		mkdir -p $(SDK_DIR)/cmdline-tools; \
		unzip -q $(SDK_TOOLS_ZIP) -d $(SDK_DIR)/cmdline-tools; \
		mv $(SDK_DIR)/cmdline-tools/cmdline-tools $(SDK_DIR)/cmdline-tools/latest; \
		rm $(SDK_TOOLS_ZIP); \
		echo "Installing required SDK components..."; \
		yes | $(SDK_DIR)/cmdline-tools/latest/bin/sdkmanager --sdk_root=$(SDK_DIR) "platform-tools" "platforms;android-34" "build-tools;34.0.0"; \
	fi
	@echo "Generating local.properties..."
	@echo "sdk.dir=$(SDK_DIR)" > local.properties
	@echo "Android SDK setup complete!"

debug: setup-sdk
	gradle assembleDebug --no-daemon --stacktrace

release: setup-sdk
	gradle assembleRelease --no-daemon --stacktrace

clean:
	gradle clean --no-daemon
