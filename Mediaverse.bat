@echo off
cd /d "%~dp0"
java --add-modules javafx.controls,javafx.fxml,javafx.media -jar target\MediaVerse.jar
pause