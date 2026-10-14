
ifeq ($(OS),Windows_NT)
GRADLE=./gradlew.bat
else
GRADLE=./gradlew
endif

all :
	$(GRADLE) --configuration-cache
	$(GRADLE) assembleDebug
	cp -v app/build/outputs/apk/debug/app-debug.apk .

clean :
	find . -iname "*\.class" -type f -exec rm -v {} \;
	$(GRADLE) clean

distclean : clean
	find . -iname "*\.apk" -type f -exec rm -v {} \;

