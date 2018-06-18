# Image-Convert Workflow Operation

## Description
With the image-convert workflow operation it is possible to run ffmpeg encoding operations on attachments.
The best given example is to convert images (as these are attachments) from one format to an other.

This operation expects an attachment as input and creates one attachments as output.


## Parameter Table
Source tags and flavors can be used in combination.

|configuration keys|example|description|default value|
|------------------|-------|-----------|-------------|
|**source-tags***|preview+player,preview+search|Comma separated list of tags of attachments to be selected as input.|EMPTY|
|**source-flavors***|*/image|Comma separated list of flavors of attachments to be selected as input.|EMPTY|
|tags-and-flavors|false|Wether to select elements with the given tags and flavors (set this value to `true`) or select elements with tags or flavors (set this value to `false`)|false|
|target-tags|+preview-converted,-preview+player|Apply these (comma separated) tags to the output attachments. If a target-tag starts with a '-', it will be removed from preexisting tags, if a target-tag starts with a '+', it will be added to preexisting tags. If there is no prefix, all preexisting tags are removed and replaced by the target-tags.|EMPTY|
|**target-flavor***|*/image+converted|Apply these flavor to the output attachments.|EMPTY|
|**encoding-profile***|jpeg-player,jpeg-search|A comma separated list of encoding profiles to be applied to each input attachment.|EMPTY|

The options marked with * are requeried.

## Operation Example

    <operation
      id="image-convert"
      fail-on-error="true"
      exception-handler-workflow="error"
      description="Resize images to fixed size">
      <configurations>
        <configuration key="source-tags">player</configuration>
        <configuration key="source-flavors">*/preview</configuration>
        <configuration key="tags-and-flavors">true</configuration>
        <configuration key="target-tags"></configuration>
        <configuration key="target-flavor">*/preview+player</configuration>
        <configuration key="encoding-profile">preview-regular.image,preview-small.image</configuration>
      </configurations>
    </operation>

### Sample Encoding Profile

    # Player preview image regular size
    profile.preview-regular.image.name = player preview image regular size
    profile.preview-regular.image.input = image
    profile.preview-regular.image.output = image
    profile.preview-regular.image.suffix = -preview-regular.jpg
    profile.preview-regular.image.mimetype = image/jpeg
    profile.preview-regular.image.ffmpeg.command = -i #{in.video.path} -vf scale=480:-2 #{out.dir}/#{out.name}#{out.suffix}
