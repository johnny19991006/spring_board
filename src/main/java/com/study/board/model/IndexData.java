package com.study.board.model;

public class IndexData {
    private String rt_cd;
    private String msg_cd;
    private String msg1;
    private Object output1;
    private Object[] output2;

    public Object getOutput1() {
        return output1;
    }
    public void setOutput1(Object output1) {
        this.output1 = output1;
    }
    public Object[] getOutput2() {
        return output2;
    }
    public void setOutput2(Object[] output2) {
        this.output2 = output2;
    }
    public String getRt_cd() {
        return rt_cd;
    }
    public void setRt_cd(String rt_cd) {
        this.rt_cd = rt_cd;
    }
    public String getMsg_cd() {
        return msg_cd;
    }
    public void setMsg_cd(String msg_cd) {
        this.msg_cd = msg_cd;
    }
    public String getMsg1() {
        return msg1;
    }
    public void setMsg1(String msg1) {
        this.msg1 = msg1;
    }
}