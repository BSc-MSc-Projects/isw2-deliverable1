package mainclass;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.Month;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.json.JSONException;
import org.json.JSONObject;

import org.json.JSONArray;

public class RetrieveTicktsId {
	private static String readAll(Reader rd) throws IOException {
		var sb = new StringBuilder();
		int cp;
		while ((cp = rd.read()) != -1) {
			sb.append((char) cp);
		}
		return sb.toString();
	}

	public static JSONArray readJsonArrayFromUrl(String url) throws IOException, JSONException {
		InputStream is = new URL(url).openStream();
		try(var rd = new BufferedReader(new InputStreamReader(is, 
				StandardCharsets.UTF_8))){
			String jsonText = readAll(rd);
			return new JSONArray(jsonText);
		} finally {
			is.close();
		}
	}

	public static JSONObject readJsonFromUrl(String url) throws IOException, JSONException {
		InputStream is = new URL(url).openStream();
		try (var rd = new BufferedReader(new InputStreamReader(is, 
				StandardCharsets.UTF_8))){
			String jsonText = readAll(rd);
			return new JSONObject(jsonText);
		} finally {
			is.close();
		}
	}
	
	
	private static String parseDate(String datetime){
		var i = 0;
		var sb = new StringBuilder();
		   
		while(datetime.charAt(i) != 'T') {
			sb.append(datetime.charAt(i));
			i++;
		}
		return sb.toString();
	}

	
	private static void fillCsvWithMetrics(String projName, List<LocalDate> resDates) {
		String cons =  projName + " proc_ctr_chart.csv";
		var f = new File(cons);
		var sb = new StringBuilder();
		
		int y;
		Month m;
		int currY;
		Month currMonth;
		
		LocalDate ld;
		var totRes = 0;
		List<Integer> values = new ArrayList<>();
		List<String> monthsValues = new ArrayList<>();
		
		ld = resDates.get(0);
		currMonth = ld.getMonth();
		currY = ld.getYear();
		
		totRes++;
		for(var i = 1; i < resDates.size(); i++) {
			ld = resDates.get(i);
			m = ld.getMonth();
			y = ld.getYear();
			
			if(m == currMonth && y == currY) {
				totRes++;
			}
			else {
				sb.append(currMonth.toString() + "-"+currY+","+totRes);
				monthsValues.add(sb.toString());
				sb.delete(0, sb.length());
				
				values.add(totRes);
				totRes = 1;
				currMonth = m;
				currY = y;
			}
		}
		
		//compute upper and lower limits
		Double variance = 0.0;
		Double mean = 0.0;
		for(Integer i : values) {
			mean += i;
		}
		mean = mean/values.size();
		
		for(Integer i : values) {
			variance += (i - mean)*(i - mean);
		}
		variance = variance/values.size();
		
		
		Double lowerLim = mean - 3*Math.sqrt(variance);
		Double upperLim = mean + 3*Math.sqrt(variance);
		
		if(lowerLim < 0.0)
			lowerLim = 0.0;
		
		try (var fw = new FileWriter(f.getAbsoluteFile(), true);
				var bw = new BufferedWriter(fw)){
			bw.write("Month-year,Number of fixed tickets,Proc Ctr Char lower limit,"
					+ "Proc Ctr Char upper limit");
			bw.newLine();
			
			for(String s : monthsValues) {
				bw.write(s + "," + lowerLim + ","+upperLim);
				bw.newLine();
			}
		}catch (FileNotFoundException e) {
				Logger.getLogger("LAB").log(Level.WARNING, "Cannot find the file\n");
		} catch (IOException e) {
				Logger.getLogger("LAB").log(Level.WARNING, e.getCause().getMessage(), e.getMessage());
		}
	}
	
	
	private void mergeSort(LocalDate[] a, int left, int right) {
		if(left < right) {
			var center = ((left+right)/2);
			mergeSort(a, left, center);
			mergeSort(a, center+1, right);
			merge(a, left, center, right);
		}
	}
	
	
	public void merge(LocalDate[] a, int left, int center, int right) {
		var i = left;
	    var j = center + 1;
	    var k = 0;
	    var b = new LocalDate[right-left+1];

	    while (i <= center && j <= right) {
	       if(a[i].isBefore(a[j]) ||
	    		   a[i].isEqual(a[j])) {
	          b[k] = a[i];
	          i = i + 1;
	       }
	       else {
	    	   b[k] = a[j];
	    	   j = j + 1;
	       }
	       k = k + 1;
	    }

	    while (i <= center) {
	       b[k] = a[i];
	       i = i + 1;
	       k = k + 1;
	    }
	    while(j <= right) {
	       b[k] = a[j];
	       j = j + 1;
	       k = k + 1;
	    }

	    for (k = left; k <= right; k++)
	       a[k] = b[k-left];
	}
	
	  
	public static void main(String[] args) throws IOException, JSONException {
			   
		var projName = "CACTUS";
		Integer j = 0; 
		Integer i = 0; 
		Integer total = 1;
		
		var rvTicks = new RetrieveTicktsId();
		
		List<LocalDate> tickDates = new ArrayList<>();
		//Get JSON API for closed bugs w/ AV in the project
		do {
			//Only gets a max of 1000 at a time, so must do this multiple times if bugs >1000
			j = i + 1000;
			
			//%22AND%22issueType%22=%22Bug%22
			
			String url = "https://issues.apache.org/jira/rest/api/2/search?jql=project=%22"
					+ projName + "%22AND(%22status%22=%22closed%22OR"
					+ "%22status%22=%22resolved%22)AND%22resolution%22=%22fixed%22&fields=key,resolutiondate,versions,created&startAt="
					+ i.toString() + "&maxResults=" + j.toString();
			JSONObject json = readJsonFromUrl(url);
			var issues = json.getJSONArray("issues");
			total = json.getInt("total");
			for (; i < total && i < j; i++) {
				//Iterate through each bug
				
				var fields = new JSONObject(issues.getJSONObject(i%1000).get("fields").toString());
				tickDates.add(LocalDate.parse(parseDate(fields.getString("resolutiondate"))));
	         }  
		} while (i < total);
		
		var array = new LocalDate[tickDates.size()];
		tickDates.toArray(array);
		rvTicks.mergeSort(array, 0, array.length-1);
		
		tickDates = Arrays.asList(array);
		
		fillCsvWithMetrics(projName, tickDates);
	}
	 
}
