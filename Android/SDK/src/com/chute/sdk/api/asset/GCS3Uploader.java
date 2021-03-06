﻿// Copyright (c) 2011, Chute Corporation. All rights reserved.
//
//  Redistribution and use in source and binary forms, with or without modification,
//  are permitted provided that the following conditions are met:
//
//     * Redistributions of source code must retain the above copyright notice, this
//       list of conditions and the following disclaimer.
//     * Redistributions in binary form must reproduce the above copyright notice,
//       this list of conditions and the following disclaimer in the documentation
//       and/or other materials provided with the distribution.
//     * Neither the name of the  Chute Corporation nor the names
//       of its contributors may be used to endorse or promote products derived from
//       this software without specific prior written permission.
//
//  THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
//  ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
//  WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
//  IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
//  INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
//  BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
//  DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
//  LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
//  OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
//  OF THE POSSIBILITY OF SUCH DAMAGE.
//
package com.chute.sdk.api.asset;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapFactory.Options;
import android.util.Log;

import com.chute.sdk.api.asset.CountingInputStreamEntity.UploadListener;
import com.chute.sdk.model.GCUploadToken;
import com.chute.sdk.utils.GCConstants;
import com.darko.imagedownloader.Utils;

public class GCS3Uploader {

    private static final String TAG = GCS3Uploader.class.getSimpleName();

    private int bytesRead, bytesAvailable, bufferSize;
    private byte[] buffer;
    private final int maxBufferSize = 1 * 1024;
    private final HttpURLConnection connection = null;
    private final DataOutputStream outputStream = null;
    private URL url;
    private final GCUploadProgressListener onProgressUpdate;
    private GCUploadToken token;

    public GCS3Uploader(GCUploadProgressListener onProgressUpdate) {
	this.onProgressUpdate = onProgressUpdate;
    }

    public void startUpload(GCUploadToken token) throws IOException {
	this.token = token;
	this.url = new URL(token.getUploadUrl());
	if (onProgressUpdate != null) {
	    try {
		Options decodeInBounds = Utils.decodeInBounds(new File(token.getFilepath()), 75);
		final Bitmap thumbnail = BitmapFactory.decodeFile(token.getFilepath(),
			decodeInBounds);
		onProgressUpdate
			.onUploadStarted(token.getAssetId(), token.getFilepath(), thumbnail);
	    } catch (Exception e) {
		Log.w(TAG, "", e);
		onProgressUpdate.onUploadStarted(token.getAssetId(), token.getFilepath(), null);
	    }
	}
	startUpload();
	if (onProgressUpdate != null) {
	    onProgressUpdate.onUploadFinished(token.getAssetId(), token.getFilepath());
	}
    }

    private void startUpload() throws IOException {
	HttpPut request = new HttpPut(url.toString());
	File file = new File(token.getFilepath());
	FileInputStream fileInputStream = new FileInputStream(file);
	// request.addHeader("Content-Length", String.valueOf(file.length()));
	request.addHeader("Date", token.getDate());
	request.addHeader("Authorization", token.getSignature());
	request.addHeader("Content-Type", "image/jpg");
	request.addHeader("x-amz-acl", "public-read");
	CountingInputStreamEntity countingInputStreamEntity = new CountingInputStreamEntity(
		fileInputStream, file.length());
	final long total = file.length();
	countingInputStreamEntity.setUploadListener(new UploadListener() {

	    @Override
	    public void onChange(long current) {
		onProgressUpdate.onProgress(total, current);
	    }
	});
	request.setEntity(countingInputStreamEntity);
	executeRequest(request);
    }

    private void executeRequest(HttpUriRequest request) throws IOException {

	DefaultHttpClient client = generateClient();
	HttpResponse httpResponse;
	try {

	    httpResponse = client.execute(request);

	    int serverResponseCode;

	    serverResponseCode = httpResponse.getStatusLine().getStatusCode();
	    if (GCConstants.DEBUG) {
		Log.e("ResponseS3: ", String.valueOf(serverResponseCode));
	    }
	    if (serverResponseCode != 200)// 200 Staus OK
	    {
		throw new IOException();
	    }
	} catch (IOException e) {
	    throw e;
	} catch (RuntimeException ex) {
	    // In case of an unexpected exception you may want to abort
	    // the HTTP request in order to shut down the underlying
	    // connection immediately.
	    request.abort();
	    throw ex;
	} finally {
	    // Closing the input stream will trigger connection release
	    client.getConnectionManager().shutdown();
	}
    }

    private static DefaultHttpClient generateClient() {
	HttpParams httpParameters = new BasicHttpParams();
	// Set the timeout in milliseconds until a connection is established.
	HttpConnectionParams.setConnectionTimeout(httpParameters, 8000);
	// Set the default socket timeout (SO_TIMEOUT)
	// in milliseconds which is the timeout for waiting for data.
	HttpConnectionParams.setSoTimeout(httpParameters, 40000);
	final DefaultHttpClient httpClient = new DefaultHttpClient(httpParameters);
	return httpClient;
    }
}
