package util;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import play.Logger;
import play.libs.IO;
import play.mvc.Http.Request;
import exception.QuickException;

public class TcoffeeHelper {
	
	public static class ResultHtml {
		public String style;
		public String body;
	}
	

	public static ResultHtml parseHtml( File file )  {
		try {
			return parseHtml(IO.readContentAsString(file));
		} catch (IOException e) {
			throw new QuickException(e, "Unable to parse html file: '%s'", file);
		}
	}
	
	public static ResultHtml parseHtml(String html) {
		Check.notNull(html, "Argument 'html' cannot be null");
		Pattern RE_TCOFFEE_HTML = Pattern.compile("^[\\s\\S]*<style>([\\s\\S]*)</style>[\\s\\S]*<body>([\\s\\S]*)</body>[\\s\\S]*$");
		ResultHtml result = null; 
		Matcher match = RE_TCOFFEE_HTML.matcher(html);
		if(match.matches()) {
			String style = match.group(1);
			String body = match.group(2);;

			body = fixIOsDashProblem( body );
			
			/* remove the cpu time */
			body = body.replaceFirst("<span[^>]*>CPU&nbsp;TIME:[^<]*</span><br>","");
			
			/* final assignment */
			result = new ResultHtml();
			result.style = style;
			result.body = body;
			
		}
		
		return result;
	}

	/* replace all - (dash) text char with enity &dash; due to a bug in iOS html rendering for fixed fonts */ 
	private static String fixIOsDashProblem(String body) {
		String userAgent = null;
		try { 
			userAgent = Request.current().headers.get("user-agent").value();
		}
		catch( Exception e ) { 
			Logger.warn("Unable to detect User-Agent");
		}

		if(userAgent==null || !userAgent.contains("iPhone OS") ) { 
			return body;
		}
		
		/* 
		 * replace all '-' chars with &dash;
		 */
		return  Utils.match(body, "(<[^>]+>)(\\-+)(</[^>]+>)", new Utils.MatchAction() {

			public String replace(List<String> groups) { 
				StringBuilder result = new StringBuilder();
				result.append(groups.get(1)); 					
				for( int i=0, c=groups.get(2).length(); i<c; i++ ) { 
					result.append("&dash;");
				}
				result.append(groups.get(3));
				return result.toString();
			}
		});

	}

	
}
