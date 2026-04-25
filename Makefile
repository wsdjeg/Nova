# Nova Android Chat App - Makefile
# 参考 .github/workflows/android.yml

# 配置变量
GRADLE := gradle
GRADLE_OPTS := --no-daemon --stacktrace
JAVA_VERSION := 17

# APK 输出路径
DEBUG_APK := app/build/outputs/apk/debug/app-debug.apk
OUTPUT_APK := app/build/outputs/apk/debug/ChatApp.apk

# Keystore 配置
DEBUG_KEYSTORE := $(HOME)/.android/debug.keystore

# 默认目标
.PHONY: all
all: build

# 构建调试版本 APK
.PHONY: build
build: keystore
	@echo "🔨 Building Debug APK..."
	$(GRADLE) assembleDebug $(GRADLE_OPTS)
	@echo "✅ Build complete: $(DEBUG_APK)"

# 生成 debug keystore（如果不存在）
.PHONY: keystore
keystore:
	@mkdir -p $(HOME)/.android
	@if [ ! -f $(DEBUG_KEYSTORE) ]; then \
		echo "🔑 Generating debug keystore..."; \
		keytool -genkey -v -keystore $(DEBUG_KEYSTORE) \
			-alias androiddebugkey \
			-storepass android \
			-keypass android \
			-keyalg RSA -keysize 2048 -validity 10000 \
			-dname "CN=Android Debug,O=Android,C=US"; \
		echo "✅ Debug keystore created"; \
	else \
		echo "✅ Debug keystore exists"; \
	fi

# 清理构建
.PHONY: clean
clean:
	@echo "🧹 Cleaning build..."
	$(GRADLE) clean
	@echo "✅ Clean complete"

# 运行测试
.PHONY: test
test:
	@echo "🧪 Running tests..."
	$(GRADLE) test $(GRADLE_OPTS)
	@echo "✅ Tests complete"

# Lint 检查
.PHONY: lint
lint:
	@echo "🔍 Running lint..."
	$(GRADLE) lint $(GRADLE_OPTS)
	@echo "✅ Lint complete"

# 构建并重命名 APK
.PHONY: apk
apk: build
	@echo "📦 Renaming APK..."
	@cp $(DEBUG_APK) $(OUTPUT_APK)
	@echo "✅ APK ready: $(OUTPUT_APK)"

# 获取版本信息
.PHONY: version
version:
	@echo "📋 Version Info:"
	@echo "  VERSION_NAME: $$(grep 'versionName' app/build.gradle | head -1 | sed 's/.*versionName[[:space:]]*["'"'"']\([^"'"'"']*\).*/\1/')"
	@echo "  VERSION_CODE: $$(grep 'versionCode' app/build.gradle | head -1 | sed 's/.*versionCode[[:space:]]*\([0-9]*\).*/\1/')"

# 完整构建流程（CI 模式）
.PHONY: ci
ci: clean build
	@echo "🚀 CI build complete"

# 帮助信息
.PHONY: help
help:
	@echo "Nova Android Chat App - Makefile"
	@echo ""
	@echo "可用目标:"
	@echo "  make build    - 构建调试版本 APK"
	@echo "  make clean    - 清理构建文件"
	@echo "  make test     - 运行测试"
	@echo "  make lint     - 运行 lint 检查"
	@echo "  make apk      - 构建并重命名 APK"
	@echo "  make version  - 显示版本信息"
	@echo "  make ci       - CI 完整构建流程"
	@echo "  make help     - 显示此帮助信息"
	@echo ""
	@echo "环境要求:"
	@echo "  - JDK $(JAVA_VERSION)"
	@echo "  - Gradle 8.0+"
