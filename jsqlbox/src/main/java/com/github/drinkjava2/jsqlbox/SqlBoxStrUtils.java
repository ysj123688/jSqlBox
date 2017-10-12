/*
 * Copyright (C) 2016 Yong Zhu.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at http://www.apache.org/licenses/LICENSE-2.0 Unless required by
 * applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS
 * OF ANY KIND, either express or implied. See the License for the specific
 * language governing permissions and limitations under the License.
 */
package com.github.drinkjava2.jsqlbox;

/**
 * DebugUtils only for debug, will delete it
 * 
 * @author Yong Zhu
 * @since 1.0.0
 */
public abstract class SqlBoxStrUtils {

	/** Camel string change to lower case underline string, "AbcDef" to "abc_def" */
	public static String camelToLowerCaseUnderline(String name) {
		StringBuilder sb = new StringBuilder();
		if (name != null && name.length() > 0) {
			sb.append(name.substring(0, 1).toLowerCase());
			for (int i = 1; i < name.length(); i++) {
				String s = name.substring(i, i + 1);
				char c = s.substring(0, 1).toCharArray()[0];
				if (c >= 'A' && c <= 'Z')
					sb.append("_");
				sb.append(s.toLowerCase());
			}
		}
		return sb.toString();
	}

	/**
	 * Build "?,?,?....?" String according given howmanyQuestions
	 * @param howmanyQuestions
	 * @return "?,?,?....?" String
	 */
	public static String getQuestionsStr(int howmanyQuestions) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < howmanyQuestions; i++) {
			sb.append("?,");
		}
		if (sb.length() > 0)
			sb.setLength(sb.length() - 1);
		return sb.toString();
	}
}