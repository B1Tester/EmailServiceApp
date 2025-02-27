package com.farmers.ecom.email.dto;


import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public class EmailRequestDTO {
    private String subject;
    private String bodyText;
    private String fromEmail;
    private List<String> toEmail;
    private List<String> ccEmail;
    private List<AttachmentDTO> attachments;

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getBodyText() {
        return bodyText;
    }

    public void setBodyText(String bodyText) {
        this.bodyText = bodyText;
    }

    public String getFromEmail() {
        return fromEmail;
    }

    public void setFromEmail(String fromEmail) {
        this.fromEmail = fromEmail;
    }

    public List<String> getToEmail() {
        return toEmail;
    }

    public void setToEmail(List<String> toEmail) {
        this.toEmail = toEmail;
    }

    public List<String> getCcEmail() {
        return ccEmail;
    }

    public void setCcEmail(List<String> ccEmail) {
        this.ccEmail = ccEmail;
    }

    public List<AttachmentDTO> getAttachments() {
        return attachments;
    }

    public void setAttachments(List<AttachmentDTO> attachments) {
        this.attachments = attachments;
    }
}

