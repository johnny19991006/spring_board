package com.study.board.model;

public class OauthInfo {
    private String grant_type;
    private String appkey;
    private String appsecret;

    public String getGrant_type() {
        return grant_type;
    }
    public void setGrant_type(String grant_type) {
        this.grant_type = grant_type;
    }
    public String getAppkey() {
        return appkey;
    }
    public void setAppkey(String appkey) {
        this.appkey = appkey;
    }
    public String getAppsecret() {
        return appsecret;
    }
    public void setAppsecret(String appsecret) {
        this.appsecret = appsecret;
    }
}