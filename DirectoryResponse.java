package com.farmers.ecom.email.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class DirectoryResponse {
    private String kind;
    private String etag;
    private List<UserEntry> users;

    // Getters and setters
    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }
    public String getEtag() { return etag; }
    public void setEtag(String etag) { this.etag = etag; }
    public List<UserEntry> getUsers() { return users; }
    public void setUsers(List<UserEntry> users) { this.users = users; }

    public static class UserEntry {
        private String id;
        @JsonProperty("primaryEmail")
        private String primaryEmail;
        private Names name;

        // Getters and setters
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getPrimaryEmail() { return primaryEmail; }
        public void setPrimaryEmail(String primaryEmail) { this.primaryEmail = primaryEmail; }
        public Names getName() { return name; }
        public void setName(Names name) { this.name = name; }
    }

    public static class Names {
        @JsonProperty("givenName")
        private String givenName;
        @JsonProperty("familyName")
        private String familyName;
        @JsonProperty("fullName")
        private String fullName;

        // Getters and setters
        public String getGivenName() { return givenName; }
        public void setGivenName(String givenName) { this.givenName = givenName; }
        public String getFamilyName() { return familyName; }
        public void setFamilyName(String familyName) { this.familyName = familyName; }
        public String getFullName() { return fullName; }
        public void setFullName(String fullName) { this.fullName = fullName; }
    }
}