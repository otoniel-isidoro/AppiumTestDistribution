#!/usr/bin/env bash
echo "Copying generated output in target inside webContent->app"
FILE=./webContent/app/src/data/Report.json
if [ -d "$FILE" ]; then
    printf '%s\n' "Removing file ($FILE)"
    rm -rf "$FILE"
fi
cp -f ./target/Report.json ./webContent/app/src/data
./screenshot_copy.sh
echo "Generating report..."
(cd webContent/app && npm install)
(cd webContent/app && npm run build)
cp -R ./webContent/app/build/ ./src/main/resources/report
echo "Report generated inside: ./main/resources/report"

