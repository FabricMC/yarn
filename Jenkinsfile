node {
   stage 'Checkout'

   git url: 'https://github.com/FabricMC/pomf.git'

   stage 'Build'

   sh "rm -rf build/libs/"
   sh "chmod +x gradlew"
   sh "./gradlew build"

   stage "Archive artifacts"

   archive 'build/libs/*'
}
