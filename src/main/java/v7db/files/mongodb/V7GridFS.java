/**
 * Copyright (c) 2011-2012, Thilo Planz. All rights reserved.
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

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.bson.BSONObject;
import org.bson.types.ObjectId;

import v7db.files.ContentStorageFacade;
import v7db.files.spi.Content;
import v7db.files.spi.ContentPointer;

import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBObject;
import com.mongodb.WriteConcern;
import com.mongodb.WriteResult;

public class V7GridFS {

	private final DBCollection files;

	private final ContentStorageFacade storage;

	public static final String COLLECTION_NAME_FILES = "v7files.files";

	public V7GridFS(DB db) {
		files = db.getCollection(COLLECTION_NAME_FILES);
		storage = new ContentStorageFacade(new MongoContentStorage(db),
				new MongoReferenceTracking(db));
	}

	public V7File getFile(String... path) {

		// the filesystem root
		V7File parentFile = V7File.lazy(this, path[0], null);

		if (path.length == 1) {
			return parentFile;
		}

		DBObject metaData;
		// directly under the root
		if (path.length == 2) {
			metaData = files.findOne(new BasicDBObject("parent", path[0])
					.append("filename", path[1]));
		}

		else {
			List<String> filenames = Arrays.asList(path)
					.subList(1, path.length);
			List<DBObject> candidates = files.find(
					new BasicDBObject("filename", new BasicDBObject("$in",
							filenames))).toArray();
			// we need to have at least one candidate for every path component
			if (candidates.size() < filenames.size())
				return null;

			Object parent = path[0];

			metaData = null;
			path: for (String fileName : filenames) {
				for (DBObject c : candidates) {
					if (parent.equals(c.get("parent"))
							&& fileName.equals(c.get("filename"))) {
						parent = c.get("_id");
						parentFile = new V7File(this, metaData, parentFile);
						metaData = c;

						continue path;
					}
				}
				return null;
			}
		}

		if (metaData == null)
			return null;
		return new V7File(this, metaData, parentFile);
	}

	/**
	 * @param data
	 *            can be null, for a file without content (e.g. a folder)
	 * @param parentFileId
	 * @param filename
	 * @return
	 * @throws IOException
	 */
	public ObjectId addFolder(Object parentFileId, String filename)
			throws IOException {
		return addFile(null, 0, 0, parentFileId, filename, null);
	}

	/**
	 * @param data
	 *            can be null, for a file without content (e.g. a folder)
	 * @param parentFileId
	 * @param filename
	 * @return
	 * @throws IOException
	 */
	public ObjectId addFile(byte[] data, Object parentFileId, String filename,
			String contentType) throws IOException {
		if (data == null)
			return addFile(null, 0, 0, parentFileId, filename, contentType);
		return addFile(data, 0, data.length, parentFileId, filename,
				contentType);
	}

	public ObjectId addFile(ContentPointer data, Object parentFileId,
			String filename, String contentType) throws IOException {
		if (data == null)
			return addFile(null, 0, 0, parentFileId, filename, contentType);

		ObjectId fileId = new ObjectId();
		BasicDBObject metaData = new BasicDBObject("parent", parentFileId)
				.append("_id", fileId);

		metaData.putAll(storage.updateBackRefs(data, fileId, filename,
				contentType));

		insertMetaData(metaData);
		return fileId;
	}

	public ObjectId addFile(byte[] data, int offset, int len,
			Object parentFileId, String filename, String contentType)
			throws IOException {

		ObjectId fileId = new ObjectId();
		BasicDBObject metaData = new BasicDBObject("parent", parentFileId)
				.append("_id", fileId);

		metaData.putAll(storage.inlineOrInsertContentsAndBackRefs(100, data,
				offset, len, fileId, filename, contentType));

		insertMetaData(metaData);
		return fileId;
	}

	/**
	 * will close the InputStream before returning
	 */
	public ObjectId addFile(InputStream data, Object parentFileId,
			String filename, String contentType) throws IOException {
		ObjectId fileId = new ObjectId();
		BasicDBObject metaData = new BasicDBObject("parent", parentFileId)
				.append("_id", fileId);

		metaData.putAll(storage.insertContentsAndBackRefs(data, fileId,
				filename, contentType));

		insertMetaData(metaData);
		return fileId;
	}

	public List<V7File> getChildren(V7File parent) {
		List<V7File> children = new ArrayList<V7File>();
		for (DBObject child : files.find(new BasicDBObject("parent", parent
				.getId()))) {
			children.add(new V7File(this, child, parent));
		}
		return children;
	}

	private void insertMetaData(DBObject metaData) throws IOException {
		metaData.put("_version", 1);
		metaData.put("created_at", new Date());
		WriteResult result = files.insert(WriteConcern.SAFE, metaData);
		String error = result.getError();
		if (error != null)
			throw new IOException(error);
	}

	void updateMetaData(DBObject metaData) throws IOException {
		metaData.put("updated_at", new Date());
		try {
			Vermongo.update(files, metaData);
		} catch (UpdateConflictException e) {
			throw new IOException(e);
		}

	}

	void updateContents(DBObject metaData, byte[] contents) throws IOException {
		updateContents(metaData, contents, 0, contents == null ? 0
				: contents.length);
	}

	void updateContents(DBObject metaData, ContentPointer newContents)
			throws IOException {
		ContentPointer oldContents = getContentPointer(metaData);

		if (newContents.contentEquals(oldContents))
			return;

		String filename = (String) metaData.get("filename");
		String contentType = (String) metaData.get("contentType");
		Object fileId = metaData.get("_id");

		BSONObject newContent = storage.updateBackRefs(newContents, fileId,
				filename, contentType);

		metaData.removeField("sha");
		metaData.removeField("length");
		metaData.removeField("in");

		metaData.putAll(newContent);

		updateMetaData(metaData);
	}

	void insertContents(DBObject metaData, ContentPointer newContents)
			throws IOException {

		String filename = (String) metaData.get("filename");
		String contentType = (String) metaData.get("contentType");
		Object fileId = metaData.get("_id");

		if (newContents != null) {
			BSONObject newContent = storage.updateBackRefs(newContents, fileId,
					filename, contentType);

			metaData.removeField("sha");
			metaData.removeField("length");
			metaData.removeField("in");

			metaData.putAll(newContent);
		}

		insertMetaData(metaData);
	}

	/**
	 * read into the buffer, continuing until the stream is finished or the
	 * buffer is full.
	 * 
	 * @return the number of bytes read, which could be 0 (not -1)
	 * @throws IOException
	 */
	static int readFully(InputStream data, byte[] buffer) throws IOException {
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

	void updateContents(DBObject metaData, InputStream contents, Long size)
			throws IOException {
		if (contents == null) {
			updateContents(metaData, (byte[]) null);
			return;
		}
		if (size != null) {
			if (size <= 1024 * 1024) {
				updateContents(metaData, IOUtils.toByteArray(contents, size));
				return;
			}
		}

		updateContents(metaData, contents);
	}

	private void updateContents(DBObject metaData, InputStream contents)
			throws IOException {

		Object fileId = metaData.get("_id");
		ContentPointer oldContents = getContentPointer(metaData);
		String filename = (String) metaData.get("filename");
		String contentType = (String) metaData.get("contentType");

		BSONObject newContent = storage.insertContentsAndBackRefs(contents,
				fileId, filename, contentType);

		// check if it has changed
		ContentPointer newContents = getContentPointer(newContent);
		if (newContents.contentEquals(oldContents))
			return;

		metaData.removeField("sha");
		metaData.removeField("length");
		metaData.removeField("in");

		metaData.putAll(newContent);

		updateMetaData(metaData);
	}

	private void updateContents(DBObject metaData, byte[] contents, int offset,
			int len) throws IOException {

		Object fileId = metaData.get("_id");
		ContentPointer oldContents = getContentPointer(metaData);
		String filename = (String) metaData.get("filename");
		String contentType = (String) metaData.get("contentType");

		// for up to 55 bytes, storing the complete file inline
		// takes less space than just storing the SHA-1 and length
		// 20 (SHA-1) + 1 (sha - in) + 6 (length) + 4 (int32) + 2*12
		// (ObjectId back-references)
		BSONObject newContent = storage.inlineOrInsertContentsAndBackRefs(55,
				contents, offset, len, fileId, filename, contentType);

		// check if it has changed
		ContentPointer newContents = getContentPointer(newContent);
		if (newContents.contentEquals(oldContents))
			return;

		metaData.removeField("sha");
		metaData.removeField("length");
		metaData.removeField("in");

		metaData.putAll(newContent);

		updateMetaData(metaData);
	}

	public V7File getChild(V7File parentFile, String childName) {
		DBObject child = files.findOne(new BasicDBObject("parent", parentFile
				.getId()).append("filename", childName));
		if (child == null)
			return null;
		return new V7File(this, child, parentFile);
	}

	void delete(V7File file) throws IOException {
		// TODO: should check the version present in the db
		Vermongo.remove(files, file.getId(), new BasicDBObject("deleted_at",
				new Date()));
		storage.insertContentsAndBackRefs(null, file.getId(), null, null);
	}

	ContentPointer getContentPointer(BSONObject metaData) {
		return storage.getContentPointer(metaData);
	}

	Content getContent(BSONObject metaData) throws IOException {
		ContentPointer pointer = storage.getContentPointer(metaData);
		return storage.getContent(pointer);
	}

}
