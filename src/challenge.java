import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Iterator;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.RAMDirectory;
import org.apache.lucene.document.Field;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;


class Match {
	private String product_name;
	private Listing[] listings;
	public String getProduct_name() {
		return product_name;
	}
	public void setProduct_name(String product_name) {
		this.product_name = product_name;
	}
	public Listing[] getListings() {
		return listings;
	}
	public void setListings(Listing[] listings) {
		this.listings = listings;
	}
}

class Listing {
	private String title;
	private String manufacturer;
	private String currency;
	private String price;
	public String getTitle() {
		return title;
	}
	public void setTitle(String title) {
		this.title = title;
	}
	public String getManufacturer() {
		return manufacturer;
	}
	public void setManufacturer(String manufacturer) {
		this.manufacturer = manufacturer;
	}
	public String getCurrency() {
		return currency;
	}
	public void setCurrency(String currency) {
		this.currency = currency;
	}
	public String getPrice() {
		return price;
	}
	public void setPrice(String price) {
		this.price = price;
	}

}

class Product {
	private String product_name;
	private String manufacturer;
	private String family;
	private String model;

	@SerializedName("announced-date")
	private String announced_date;

	public String getProduct_name() {
		return product_name;
	}

	public String getManufacturer() {
		return manufacturer;
	}

	public String getFamily() {
		return family;
	}

	public String getModel() {
		return model;
	}

}


public class challenge {

	private static String productFile = "input/products.txt";
	private static String listingFile = "input/listings.txt";
	private final static String matchFile = "result.jsonl";

	
	public static void main(String[] args){
		//get the file paths
		productFile = args[0];
		listingFile = args[1];
		
		//load the files into objects to start
		ArrayList<Product> products = populateProducts();
		ArrayList<Listing> listings = populateListings();

		//clear the result.txt file
		new File(matchFile).delete();
		
		
		/* let's match!
		 * - a listing item can only match to a single product
		 * 
		 * return a list of json
		 * {
		 *   "product_name": String
		 *   "listings": Array[Listing]
		 * }
		 */

		//populate the listings into a lucene index and then query it to pick out possible matches to products

		Directory index = new RAMDirectory();
		makeListingsIndex(index, listings);
		
		Iterator it = products.iterator();
		int hitsPerPage = 1000000;
		Gson gson = new Gson();
		
		//for each product, do a query on the search index and create a match if applicable
		while(it.hasNext()){
			Product curProduct = (Product) it.next();

			try {
				//I'm only going to look at listings where the manufacturer is found and the model exists in the title.  This should return accurate results.
				//Since it's now in a search index, I can modify my query to refine results moving forward.
				String queryString = MessageFormat.format("manufacturer:\"{0}\" AND title:\"{1}\"", 
						QueryParser.escape(curProduct.getManufacturer()), 
						QueryParser.escape(curProduct.getModel()));


				QueryParser queryParser = new QueryParser(null, new StandardAnalyzer());
				Query q = queryParser.parse(queryString);

				IndexReader reader = DirectoryReader.open(index);
				IndexSearcher searcher = new IndexSearcher(reader);
//				System.out.println(queryString);
				TopDocs docs = searcher.search(q, hitsPerPage);
				ScoreDoc[] hits = docs.scoreDocs;

//				System.out.println("Found " + hits.length + " hits.");

				//if hits > 0, create a match object
				
				if(hits.length>0){
					Match m = new Match();
					String product_name = curProduct.getProduct_name();
					Listing[] matched_listings = new Listing[hits.length];
					
					for(int i=0;i<hits.length;++i) {
						int docId = hits[i].doc;
						Document d = searcher.doc(docId);
						//					System.out.println((i + 1) + ". " + d.get("id") + "\t" + d.get("manufacturer") + "\t" + d.get("title"));
						Listing curListing = new Listing();
						String id = d.get("id");
												
						curListing.setCurrency(d.get("currency"));
						curListing.setManufacturer(d.get("manufacturer"));
						curListing.setPrice(d.get("price"));
						curListing.setTitle(d.get("title"));
						
						matched_listings[i] = curListing;
						
						//delete the entry from lucene so that it cannot be matched twice.
						deleteFromIndex(index,id);
						
					}
					m.setProduct_name(product_name);
					m.setListings(matched_listings);
					
					
					String jsonMatch = gson.toJson(m);
//					System.out.println(jsonMatch);
					
					//output the match to file
					WriteToFile(jsonMatch);
				}

			} catch (IOException e) {
				e.printStackTrace();
			} catch (ParseException e) {
				e.printStackTrace();
			}
		}

	}

	private static void deleteFromIndex(Directory index, String id) throws IOException {
		Term deleteTerm = new Term("id", id);
		
		StandardAnalyzer analyzer = new StandardAnalyzer();
		IndexWriterConfig config = new IndexWriterConfig(analyzer);
		IndexWriter w = new IndexWriter(index, config);
		w.deleteDocuments(deleteTerm);
		w.commit();
		w.close();
	}

	private static void WriteToFile(String s) {
//		System.out.println(s);
		BufferedWriter bw = null;

		try {
			bw = new BufferedWriter(new FileWriter(matchFile, true));
			bw.write(s + "\n");
//			bw.newLine();
			bw.flush();
		} catch (IOException ioe) {
			ioe.printStackTrace();
		} finally {                       // always close the file
			if (bw != null) try {
				bw.close();
			} catch (IOException ioe2) {
				// just ignore it
			}
		} // end try/catch/finally
	}	

	private static void makeListingsIndex(Directory index, ArrayList<Listing> listings) {


		StandardAnalyzer analyzer = new StandardAnalyzer();
		IndexWriterConfig config = new IndexWriterConfig(analyzer);
		try {
			IndexWriter w = new IndexWriter(index, config);
			Iterator it = listings.iterator();
			int i = 0;
			while(it.hasNext()){
				Listing listing = (Listing) it.next();
				addDoc(w, listing, i);
				i++;
			}

			w.close();

		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private static void addDoc(IndexWriter w, Listing l, int id) throws IOException {
		Document doc = new Document();
		doc.add(new StringField("id", ""+id, Field.Store.YES));
		doc.add(new TextField("title", l.getTitle(), Field.Store.YES));
		doc.add(new TextField("manufacturer", l.getManufacturer(), Field.Store.YES)); //decided to tokenize this field after analyzing the raw data
		doc.add(new StringField("currency", l.getCurrency(), Field.Store.YES));
		doc.add(new StringField("price", l.getPrice(), Field.Store.YES));
		w.addDocument(doc);
	}

	private static ArrayList<Listing> populateListings() {
		ArrayList<Listing> listings = new ArrayList<Listing>();

		try{
			FileInputStream fstream = new FileInputStream(listingFile);
			DataInputStream in = new DataInputStream(fstream);
			BufferedReader br = new BufferedReader(new InputStreamReader(in));
			String jsonString;
			while ((jsonString = br.readLine()) != null)   {
				//				System.out.println (jsonString);
				GsonBuilder gsonBuilder = new GsonBuilder();
				Gson gson = gsonBuilder.create();
				Listing listing = gson.fromJson(jsonString, Listing.class);
				listings.add(listing);
			}
			in.close();
		}catch (Exception e){
			System.err.println("Error: " + e.getMessage());
		}

		return listings;
	}

	private static ArrayList<Product> populateProducts() {
		ArrayList<Product> products = new ArrayList<Product>();

		try{
			FileInputStream fstream = new FileInputStream(productFile);
			DataInputStream in = new DataInputStream(fstream);
			BufferedReader br = new BufferedReader(new InputStreamReader(in));
			String jsonString;
			while ((jsonString = br.readLine()) != null)   {
				//				System.out.println (jsonString);
				GsonBuilder gsonBuilder = new GsonBuilder();
				Gson gson = gsonBuilder.create();
				Product product = gson.fromJson(jsonString, Product.class);
				products.add(product);
			}
			in.close();
		}catch (Exception e){
			System.err.println("Error: " + e.getMessage());
		}

		return products;
	}
}
