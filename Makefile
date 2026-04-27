.PHONY: debug release clean

debug:
	gradle assembleDebug --no-daemon --stacktrace

release:
	gradle assembleRelease --no-daemon --stacktrace

clean:
	gradle clean --no-daemon
