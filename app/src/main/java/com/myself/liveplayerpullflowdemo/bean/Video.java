package com.myself.liveplayerpullflowdemo.bean;

//Video element
public class Video {
    /**
     * video ID
     */
    private long videoId;
    /**
     * video name
     */
    private String name;
    /**
     * video address
     */
    private String uri;
    /**
     * video scale picture
     */
    private String pic;
    /**
     * video file
     */
    private int size;
    /**
     * video format
     */
    private String format;
    /**
     * video duration
     */
    private int druration;
    /**
     * video definition
     */
    private String definition;
    /**
     * video location
     */
    private boolean isLocation;

    // 是否使用硬件解码
    private boolean useHwDecoder = true;

    private boolean inLoopPlay = false;

    public Video() {
    }

    public Video(String name, String uri, String pic, int size, boolean isLocation) {
        this.name = name;
        this.uri = uri;
        this.pic = pic;
        this.size = size;
        this.isLocation = isLocation;
    }

    public void setInLoopPlay(boolean b) {
        this.inLoopPlay = b;
    }

    public boolean inLoopPlay() {
        return this.inLoopPlay;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUri() {
        return uri;
    }

    public void setUri(String uri) {
        this.uri = uri;
    }

    public String getPic() {
        return pic;
    }

    public void setPic(String pic) {
        this.pic = pic;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public int getDruration() {
        return druration;
    }

    public void setDruration(int druration) {
        this.druration = druration;
    }

    public boolean isLocation() {
        return isLocation;
    }

    public void setLocation(boolean isLocation) {
        this.isLocation = isLocation;
    }

    public long getVideoId() {
        return videoId;
    }

    public void setVideoId(long videoId) {
        this.videoId = videoId;
    }

    public String getDefinition() {
        return definition;
    }

    public void setDefinition(String definition) {
        this.definition = definition;
    }

    public boolean isUseHwDecoder() {
        return useHwDecoder;
    }

    public void setUseHwDecoder(boolean useHwDecoder) {
        this.useHwDecoder = useHwDecoder;
    }
}