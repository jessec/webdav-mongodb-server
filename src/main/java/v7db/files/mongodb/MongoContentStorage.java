/**
 * Copyright (c) 2012, Thilo Planz. All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package v7db.files.mongodb;

import static v7db.files.mongodb.QueryUtils._ID;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.bson.BSONObject;

import v7db.files.Compression;
import v7db.files.MapUtils;
import v7db.files.ZipFile;
import v7db.files.spi.Content;
import v7db.files.spi.ContentConcatenation;
import v7db.files.spi.ContentPointer;
import v7db.files.spi.ContentSHA;
import v7db.files.spi.ContentStorage;
import v7db.files.spi.GzippedContent;
import v7db.files.spi.InlineContent;
import v7db.files.spi.OffsetAndLength;
import v7db.files.spi.StorageScheme;
import v7db.files.spi.StoredContent;

import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBObject;
import com.mongodb.WriteConcern;
import com.mongodb.gridfs.GridFS;

/**
 * ContentStorage implementation that uses MongoDB documents.
 * 
 * <ul>
 * <li>The <code>_id</code> field is the content SHA-1 digest (20 bytes of
 * binary data)
 * <li>"Small" content (that does not need to be chunked) is stored in the
 * binary field <code>in</code>.
 * <li>If the data can be compressed using gzip, it will be stored in compressed
 * form as <code>zin</code>. This mode is indicated by setting the value
 * <code>gz</code> for the <code>store</code> field. The uncompressed length is
 * given in the <code>length</code> field.
 * <li>"Large" content is stored as the concatenation of chunks stored
 * out-of-band (in other documents). For very large documents this can also
 * become nested.
 * <li>Other types of "out-of-band" storage schemes are possible and can be
 * provided by extension code.
 * </ul>
 * 
 * @see https://github.com/thiloplanz/v7files/wiki/StorageFormat
 * 
 * 
 */

public class MongoContentStorage implements ContentStorage {

	private static final int chunkSize = GridFS.DEFAULT_CHUNKSIZE;

	private final DBCollection contentCollection;

	private final Map<String, StorageScheme> storageSchemes = new HashMap<String, StorageScheme>();

	public final static String DEFAULT_CONTENT_COLLECTION_NAME = "v7files.content";

	public MongoContentStorage(DB db) {
		this(db.getCollection(DEFAULT_CONTENT_COLLECTION_NAME));
	}

	public MongoContentStorage(DBCollection contentCollection) {
		this.contentCollection = contentCollection;
		storageSchemes.put("gz", new GzippedContent());
		storageSchemes.put("cat", new ContentConcatenation());
		storageSchemes.put("zip", new ZipFile.ContentFromZipFile());
	}

	public Content getContent(byte[] sha) throws IOException {
		return getContent(contentCollection.findOne(sha));
	}

	public ContentSHA findContentPointerByPrefix(byte[] shaPrefix)
			throws IOException {
		if (shaPrefix.length == 20) {
			DBObject file = contentCollection.findOne(shaPrefix);
			if (file == null)
				return null;
			Content c = getContent(file);
			return ContentSHA.forDigestAndLength(shaPrefix, c.getLength());
		}

		if (shaPrefix.length > 20)
			throw new IllegalArgumentException();

		byte[] lower = Arrays.copyOf(shaPrefix, 20); // 0-padded
		byte[] higher = Arrays.copyOf(shaPrefix, 20); // FF-padded
		for (int i = shaPrefix.length; i < higher.length; i++) {
			higher[i] = (byte) 0xFF;
		}
		List<DBObject> files = contentCollection.find(
				QueryUtils.between(_ID, lower, higher), new BasicDBObject())
				.limit(2).toArray();
		if (files.isEmpty())
			return null;
		if (files.size() == 1) {
			Content c = getContent(files.get(0));
			return ContentSHA.forDigestAndLength(
					(byte[]) files.get(0).get(_ID), c.getLength());
		}
		throw new IllegalArgumentException(Hex.encodeHexString(shaPrefix)
				+ " is not a unique SHA prefix");
	}

	public Content findContentByPrefix(byte[] shaPrefix) throws IOException {
		if (shaPrefix.length == 20)
			return getContent(shaPrefix);
		if (shaPrefix.length > 20)
			throw new IllegalArgumentException();

		byte[] lower = Arrays.copyOf(shaPrefix, 20); // 0-padded
		byte[] higher = Arrays.copyOf(shaPrefix, 20); // FF-padded
		for (int i = shaPrefix.length; i < higher.length; i++) {
			higher[i] = (byte) 0xFF;
		}

		List<DBObject> files = contentCollection.find(
				QueryUtils.between(_ID, lower, higher), new BasicDBObject())
				.limit(2).toArray();
		if (files.isEmpty())
			return null;
		if (files.size() == 1)
			return getContent(files.get(0));
		throw new IllegalArgumentException(Hex.encodeHexString(shaPrefix)
				+ " is not a unique SHA prefix");
	}

	public Content getContent(ContentPointer pointer) throws IOException {
		if (pointer == null)
			return null;
		if (pointer instanceof InlineContent)
			return (Content) pointer;

		if (pointer instanceof ContentSHA) {
			ContentSHA p = (ContentSHA) pointer;
			byte[] sha = p.getSHA();

			Content base = getContent(sha);
			if (base == null)
				throw new IllegalArgumentException("base SHA not found: "
						+ Hex.encodeHexString(sha));

			return base;

		}

		if (pointer instanceof StoredContent) {
			StoredContent p = (StoredContent) pointer;
			byte[] sha = p.getBaseSHA();

			Content base = getContent(sha);
			if (base == null)
				throw new IllegalArgumentException("base SHA not found: "
						+ Hex.encodeHexString(sha));

			if (p.getLength() != base.getLength()) {
				return new OffsetAndLength(base, 0, p.getLength());
			}

			return base;

		}
		throw new IllegalArgumentException(pointer.getClass().toString());

	}

	@SuppressWarnings("unchecked")
	private Content getContent(BSONObject data) throws IOException {
		if (data == null)
			return null;
		data.removeField("_id");
		String store = BSONUtils.getString(data, "store");
		if (store == null || "raw".equals(store)) {
			return InlineContent.deserialize(data.toMap());
		}
		StorageScheme s = storageSchemes.get(store);
		if (s != null)
			return s.getContent(this, data.toMap());
		throw new UnsupportedOperationException(store);
	}

	/**
	 * read into the buffer, continuing until the stream is finished or the
	 * buffer is full.
	 * 
	 * @return the number of bytes read, which could be 0 (not -1)
	 * @throws IOException
	 */
	private static int readFully(InputStream data, byte[] buffer)
			throws IOException {
		int read = data.read(buffer);
		if (read == -1) {
			return 0;
		}
		while (read < buffer.length) {
			int added = data.read(buffer, read, buffer.length - read);
			if (added == -1)
				return read;
			read += added;
		}
		return read;
	}

	public ContentSHA storeContent(InputStream data) throws IOException {
		try {
			MessageDigest completeSHA = MessageDigest.getInstance("SHA");
			long completeLength = 0;
			byte[] chunk = new byte[chunkSize];
			int read;
			List<ContentSHA> chunks = new ArrayList<ContentSHA>();

			while (0 < (read = readFully(data, chunk))) {
				completeSHA.update(chunk, 0, read);
				completeLength += read;
				chunks.add(storeContentChunk(chunk, 0, read));
			}
			if (chunks.isEmpty())
				return storeContentChunk(ArrayUtils.EMPTY_BYTE_ARRAY, 0, 0);

			if (chunks.size() == 1)
				return chunks.get(0);

			List<Map<String, Object>> bases = new ArrayList<Map<String, Object>>(
					chunks.size());
			for (ContentSHA c : chunks) {
				bases.add(c.serialize());
			}
			ContentSHA result = ContentSHA.forDigestAndLength(completeSHA
					.digest(), completeLength);
			long existing = contentCollection.count(new BasicDBObject(_ID,
					result.getSHA()));
			if (existing == 0) {
				contentCollection
						.insert(new BasicDBObject(_ID, result.getSHA()).append(
								"store", "cat").append("base", bases),
								WriteConcern.SAFE);
			}
			return result;
		} catch (NoSuchAlgorithmException e) {
			throw new RuntimeException(e);
		} finally {
			IOUtils.closeQuietly(data);
		}

	}

	private ContentSHA storeContentChunk(byte[] bytes, final int offset,
			final int length) throws IOException {
		ContentSHA _sha = ContentSHA.calculate(bytes, offset, length);
		byte[] sha = _sha.getSHA();

		long existing = contentCollection.count(new BasicDBObject(_ID, sha));
		if (existing == 0) {
			byte[] gzipped = Compression.gzip(bytes, offset, length);
			if (gzipped != null && gzipped.length > chunkSize)
				gzipped = null;
			if (gzipped != null) {
				bytes = null;
				contentCollection.insert(new BasicDBObject(_ID, sha).append(
						"zin", gzipped).append("store", "gz"),
						WriteConcern.SAFE);
				gzipped = null;
			} else {
				if (offset > 0 || bytes.length != length) {
					bytes = ArrayUtils.subarray(bytes, offset, offset + length);
				}
				contentCollection.insert(new BasicDBObject(_ID, sha).append(
						"in", bytes), WriteConcern.SAFE);
			}
		}
		return _sha;
	}

	public ContentPointer storeContent(Map<String, Object> storageScheme)
			throws IOException {
		StorageScheme s = storageSchemes.get(storageScheme.get("store"));
		if (s == null)
			throw new UnsupportedOperationException(storageScheme.toString());

		DBObject x = new BasicDBObject();
		for (Map.Entry<String, Object> e : storageScheme.entrySet()) {
			x.put(e.getKey(), e.getValue());
		}
		long length = BSONUtils.getRequiredLong(x, "length");
		byte[] sha = DigestUtils.sha(s.getContent(this, storageScheme)
				.getInputStream());

		long existing = contentCollection.count(new BasicDBObject(_ID, sha));
		if (existing == 0) {
			x.put(_ID, sha);
			contentCollection.insert(x, WriteConcern.SAFE);
		}
		return new StoredContent(sha, length);
	}

	/**
	 * Supported formats: 1) Serialized ContentPointers, e.g.
	 * 
	 * <pre>
	 * { in: [bytes] }
	 * </pre>
	 * 
	 * and
	 * 
	 * <pre>
	 * { sha: <sha>, length: 123 }
	 * </pre>
	 * 
	 * 2) Internal StorageScheme representations (must have {store: something}")
	 */
	public Content getContent(Map<String, Object> data) throws IOException {
		if (data == null)
			return null;
		String store = MapUtils.getString(data, "store");
		if (store == null || "raw".equals(store)) {
			if (data.containsKey("in"))
				return InlineContent.deserialize(data);
			if (data.containsKey("sha")) {
				return new StoredContent((byte[]) data.get("sha"), MapUtils
						.getRequiredLong(data, "length")).loadOrLazyLoad(this,
						8 * 1024);
			}
			throw new UnsupportedOperationException(data.toString());
		}

		StorageScheme s = storageSchemes.get(store);
		if (s == null)
			throw new UnsupportedOperationException(store);

		return s.getContent(this, data);
	}
}
