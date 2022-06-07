package org.tribot.automation;

import com.google.gson.Gson;
import com.jcraft.jsch.*;
import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Slf4j
class TribotSplash {

	private static final String TRIBOT_SPLASH_URL = "https://runeautomation.com/api/internal/products/tribot-splash/releases/latest";
	private static final String JAR_NAME = "tribot-splash.jar";
	private static final String LOCK_FILE = getAppDataDirectory() + File.separator + "tribot-splash.lock";
	private static final String FILE_PATH = getAppDataDirectory() + File.separator + JAR_NAME;

	private final HttpClient httpClient = HttpClient.newHttpClient();

	private final Map<SshSettings, Long> lastRemoteUpdate = new ConcurrentHashMap<>();

	public String update() throws IOException {
		try (final RandomAccessFile tmp = new RandomAccessFile(LOCK_FILE, "rw")) {
			tmp.getChannel().lock(); // this will be unlocked when tmp is automatically closed
			final ProductFile file = getSplashJarFile();
			final String local = getLocalHash();
			log.debug("TRiBot splash hash: " + file.getHash() + "; local file hash: " + local);
			if (!file.getHash().equals(local)) {
				System.out.println("Attempting to update local tribot-splash.jar");
				download(file);
				log.debug("Updated tribot-splash.jar");
			}
			else {
				log.debug("TRiBot splash is up-to-date");
			}
		}
		return FILE_PATH;
	}

	public String update(SshSettings sshConfig) throws IOException {
		try {
			final String local = update();
			final String localHash = getLocalHash();
			final Session session = sshConfig.createSession();
			try {
				final String remoteHome = getHome(session);
				final String remotePath = getRemotePath(remoteHome);
				final String remoteHash = getRemoteHash(session, remotePath);
				final long lastUpdate = lastRemoteUpdate.getOrDefault(sshConfig, 0L);
				// To minimize the amount of dependencies required, we will simply update every 30 mins
				// if the hash-check fails
				if ((remoteHash != null && !remoteHash.equals(localHash))
						|| System.currentTimeMillis() > lastUpdate + TimeUnit.MINUTES.toMillis(60)) {
					updateRemote(session, local, remotePath);
					lastRemoteUpdate.put(sshConfig, System.currentTimeMillis());
				}
				return remotePath;
			}
			finally {
				session.disconnect();
			}
		}
		catch (JSchException | SftpException | InterruptedException e) {
			throw new IOException(e);
		}
	}

	// Returns a non-null response only if the response is a valid hash
	private String getRemoteHash(Session session, String file) {
		try {
			final ChannelExec channel = (ChannelExec) session.openChannel("exec");
			try {
				channel.setCommand("md5sum " + '"' + file + '"');
				ByteArrayOutputStream responseStream = new ByteArrayOutputStream();
				channel.setOutputStream(responseStream);
				channel.connect();
				while (channel.isConnected()) {
					Thread.sleep(100);
				}
				final String response = responseStream.toString().toUpperCase();
				if (response.length() < 32) {
					return null;
				}
				final String checksum = response.substring(0, 32);
				if (!checksum.matches("[A-Z0-9]*")) {
					return null;
				}
				log.debug("Remote hash: {}", checksum);
				return checksum;
			}
			finally {
				channel.disconnect();
			}
		}
		catch (Exception e) {
			return null;
		}
	}

	private void updateRemote(Session session, String localPath, String remotePath) throws JSchException, SftpException {
		final ChannelSftp sftp = (ChannelSftp) session.openChannel("sftp");
		try {
			sftp.connect();
			sftp.put(localPath, remotePath);
		}
		finally {
			sftp.disconnect();
		}
	}

	private String getRemotePath(String home) {
		return home + "/tribot-splash.jar";
	}

	private String getHome(Session session) throws JSchException, InterruptedException {
		final ChannelExec channel = (ChannelExec) session.openChannel("exec");
		try {
			channel.setCommand("echo ~");
			ByteArrayOutputStream responseStream = new ByteArrayOutputStream();
			channel.setOutputStream(responseStream);
			channel.connect();
			while (channel.isConnected()) {
				Thread.sleep(100);
			}
			return responseStream.toString().replace("\n", "").replace("\r\n", "");
		}
		finally {
			channel.disconnect();
		}
	}

	private void download(ProductFile file) throws IOException {
		final HttpRequest request = HttpRequest.newBuilder(URI.create(file.getUrl()))
				.GET()
				.build();
		try {
			this.httpClient.send(request, BodyHandlers.ofFile(Path.of(FILE_PATH)));
		}
		catch (InterruptedException e) {
			throw new IllegalStateException(e);
		}
	}

	private String getLocalHash() {
		try {
			return getMD5Checksum(FILE_PATH).toUpperCase();
		}
		catch (Exception e) {
			return null;
		}
	}

	private ProductFile getSplashJarFile() throws IOException {
		final TribotProduct response = getSplashProduct();
		for (ProductFile file : response.getFiles()) {
			if (JAR_NAME.equals(file.getFileName())) {
				return file;
			}
		}
		throw new IllegalStateException("Could not find the tribot splash jar");
	}

	private TribotProduct getSplashProduct() throws IOException {
		final HttpRequest request = HttpRequest.newBuilder(URI.create(TRIBOT_SPLASH_URL))
				.GET()
				.build();
		final HttpResponse<String> response;
		try {
			response = this.httpClient.send(request, BodyHandlers.ofString());
		}
		catch (InterruptedException e) {
			throw new IllegalStateException(e);
		}
		return new Gson().fromJson(response.body(), TribotProduct.class);
	}

	private class TribotProduct {

		private final String productName;
		private final String major;
		private final String minor;
		private final String patch;

		private final ProductFile[] files;

		public TribotProduct(String productName, String major, String minor, String patch, ProductFile[] files) {
			this.productName = productName;
			this.major = major;
			this.minor = minor;
			this.patch = patch;
			this.files = files;
		}

		public String getProductName() {
			return this.productName;
		}

		public String getMajor() {
			return this.major;
		}

		public String getMinor() {
			return this.minor;
		}

		public String getPatch() {
			return this.patch;
		}

		public ProductFile[] getFiles() {
			return this.files;
		}

	}

	private class ProductFile {

		private final String fileName;
		private final String hash;
		private final String url;

		public ProductFile(String fileName, String hash, String url) {
			this.fileName = fileName;
			this.hash = hash;
			this.url = url;
		}

		public String getFileName() {
			return this.fileName;
		}

		public String getHash() {
			return this.hash;
		}

		public String getUrl() {
			return this.url;
		}
	}

	// Decompiled from tribot
	private static File getAppDataDirectory() {
		File a = null;
		final String a2 = System.getProperty("user.home");
		final String a3 = System.getProperty("os.name").toLowerCase();
		if (a3.contains("win")) {
			final String a4 = System.getenv("APPDATA");
			a = ((a4 == null || a4.length() < 1) ? new File(a2, ".tribot" + File.separatorChar)
			                                     : new File(a4, ".tribot" + File.separatorChar));
		} else if (a3.contains("solaris") || a3.contains("linux") || a3.contains("sunos") || a3.contains("unix")) {
			a = new File(a2, ".tribot" + File.separatorChar);
		} else if (a3.contains("mac")) {
			a = new File(a2, "Library" + File.separatorChar + "Application Support" + File.separatorChar + "tribot");
		} else {
			a = new File(a2, "tribot" + File.separatorChar);
		}
		if (a != null) {
			if (a.exists()) {
				return a;
			}
			if (a.mkdirs()) {
				return a;
			}
		}
		a = new File("data");
		log.debug("Couldn't create seperate application data directory. Using application data directory as: "
		                   + a.getAbsolutePath());
		log.debug("Debug; User home: " + a2 + ", os name: " + a3);
		return a;
	}

	private static byte[] createChecksum(String filename) throws Exception {

		final InputStream fis = new FileInputStream(filename);

		final byte[] buffer = new byte[1024];
		final MessageDigest complete = MessageDigest.getInstance("MD5");
		int numRead;

		do {
			numRead = fis.read(buffer);
			if (numRead > 0) {
				complete.update(buffer, 0, numRead);
			}
		}
		while (numRead != -1);

		fis.close();
		return complete.digest();
	}

	private static String getMD5Checksum(String filename) throws Exception {
		final byte[] b = createChecksum(filename);
		String result = "";
		for (int i = 0; i < b.length; i++) {
			result += Integer.toString((b[i] & 0xff) + 0x100, 16).substring(1);
		}
		return result;
	}

}
