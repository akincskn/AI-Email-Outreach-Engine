@REM Maven Wrapper startup batch script
@SETLOCAL
@SET MAVEN_PROJECTBASEDIR=%~dp0

@IF "%JAVA_HOME%"=="" (
  SET _JAVACMD=java
) ELSE (
  SET _JAVACMD="%JAVA_HOME%\bin\java.exe"
)

@%_JAVACMD% ^
  -classpath "%MAVEN_PROJECTBASEDIR%.mvn\wrapper\maven-wrapper.jar" ^
  "-Dmaven.multiModuleProjectDirectory=%MAVEN_PROJECTBASEDIR%" ^
  org.apache.maven.wrapper.MavenWrapperMain %*
