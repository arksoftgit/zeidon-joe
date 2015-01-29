#!/bin/bash

version=1.2
echo "Creating javadoc for $version"

pushd ../zeidon-dev

rm -rf zeidon-joe/target/site/apidocs > /dev/null
rm -rf android/target/site/apidocs > /dev/null
rm -rf scala/target/site/scaladocs > /dev/null

# Make sure we're matching the git branch version
git_ver=`git rev-parse --abbrev-ref HEAD`
if [[ $git_ver == ${version}.* ]]; then
    echo ""
else
    echo "Mismatching versions"
    exit
fi

mvn javadoc:javadoc

pushd scala
mvn scala:doc
popd

popd

rm -rf javadoc/$version > /dev/null

mkdir -p javadoc/$version/joe javadoc/$version/android javadoc/$version/scala
cp -r ../zeidon-dev/zeidon-joe/target/site/apidocs/* javadoc/$version/joe
cp -r ../zeidon-dev/android/target/site/apidocs/* javadoc/$version/android
cp -r ../zeidon-dev/scala/target/site/scaladocs/* javadoc/$version/scala

if [[ $1 == "--commit" ]]; then
    git add -A javadoc
    git commit -m "Updated javadoc"
    git push
fi
