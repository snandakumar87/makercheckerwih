mkdir test_script
cd test_script
git clone git://localhost:9418/MySpace/Personalization-DM
cd Personalization-DM
mvn versions:set -DnewVersion=$1
mvn clean install
cd ..
rm -rf Personalization-DM
