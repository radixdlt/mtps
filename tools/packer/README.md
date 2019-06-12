1. Download a packer binary to this folder from packer.io.

2. Create service account and get credentials for it into an *account* file. See **Running Without a Compute Engine Service Account** on https://packer.io/docs/builders/googlecompute.html to do this.

3. Download and store your JSON credentials to: `~/.config/gcloud/application_default_credentials.json`

3. Run `packer validate ./core-image-template.json`. Fix any errors encountered. 

4. Build image by running `packer build --var "BUILDER_PROJECT=<name>" ./core-image-template.json`

5. Note down the image name that is output at the end of the process. If you use the image in another project than where it is created (see *BUILDER_PROJECT* in *core-image-template.json*) make sure to make the image accessible from those accounts.
