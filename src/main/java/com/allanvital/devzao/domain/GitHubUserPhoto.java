package com.allanvital.devzao.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

/**
* @author Allan Vital (https://allanvital.com)
  */
@Entity
@Table(name = "github_user_photo")
public class GitHubUserPhoto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "github_user_id", nullable = false, unique = true)
    private GitHubUser gitHubUser;

    @Lob
    @Column(name = "photo_blob", nullable = false)
    private byte[] photoBlob;

    @Column(name = "content_type")
    private String contentType;

    protected GitHubUserPhoto() {
    }

    public GitHubUserPhoto(GitHubUser gitHubUser, byte[] photoBlob, String contentType) {
        this.gitHubUser = gitHubUser;
        this.photoBlob = photoBlob;
        this.contentType = contentType;
    }

    public Long getId() {
        return this.id;
    }

    public GitHubUser getGitHubUser() {
        return this.gitHubUser;
    }

    public byte[] getPhotoBlob() {
        return this.photoBlob;
    }

    public void setPhotoBlob(byte[] photoBlob) {
        this.photoBlob = photoBlob;
    }

    public String getContentType() {
        return this.contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }
}
