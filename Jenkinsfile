node {
   stage 'Checkout'

   checkout scm

   stage 'Build'

   sh "rm -rf build/libs/"
   sh "./gradlew build javadocJar publish --refresh-dependencies"
}
