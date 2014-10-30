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
package v7db.files.spi;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.codec.digest.DigestUtils;

public final class StoredContent implements ContentPointer {

	private final byte[] sha;

	private final long length;

	public StoredContent(byte[] sha, long length) {
		this.sha = sha.clone();
		this.length = length;
	}

	public byte[] getBaseSHA() {
		return sha.clone();
	}

	public long getLength() {
		return length;
	}

	public long getOffset() {
		return 0;
	}

	public Map<String, Object> serialize() {
		Map<String, Object> result = new HashMap<String, Object>();
		result.put("sha", getBaseSHA());
		result.put("length", length);
		return result;
	}

	public boolean contentEquals(ContentPointer otherContent) {
		if (otherContent == null || otherContent.getLength() != length)
			return false;
		if (otherContent instanceof StoredContent) {
			StoredContent sc = (StoredContent) otherContent;
			return getOffset() == sc.getOffset() && Arrays.equals(sha, sc.sha);
		}
		if (otherContent instanceof InlineContent)
			try {
				return Arrays.equals(sha, DigestUtils
						.sha(((InlineContent) otherContent).getInputStream()));
			} catch (IOException e) {
				e.printStackTrace();
				return false;
			}
		return false;
	}

	public Content loadOrLazyLoad(final ContentStorage storage,
			int loadAndCacheUntilLength) throws IOException {
		if (length <= loadAndCacheUntilLength)
			return storage.getContent(this);

		return new Content() {

			public InputStream getInputStream() throws IOException {
				return storage.getContent(StoredContent.this).getInputStream();
			}

			public InputStream getInputStream(long offset, long length)
					throws IOException {
				return storage.getContent(StoredContent.this).getInputStream(
						offset, length);
			}

			public long getLength() {
				return length;
			}

		};

	}
}
