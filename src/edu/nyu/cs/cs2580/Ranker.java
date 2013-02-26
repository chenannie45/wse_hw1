package edu.nyu.cs.cs2580;

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Vector;
import java.util.Scanner;
import java.lang.Math;

class Ranker {
  private Index _index;

  public Ranker(String index_source){
    _index = new Index(index_source);
  }

  public Vector < ScoredDocument > runquery(String query){
    Vector < ScoredDocument > retrieval_results = new Vector < ScoredDocument > ();
    for (int i = 0; i < _index.numDocs(); ++i){
  //    retrieval_results.add(runquery(query, i));
    	retrieval_results.add(runquery_tfidf(query, i));
    }
    Collections.sort(retrieval_results,Collections.reverseOrder());
    return retrieval_results;
  }

  public ScoredDocument runquery(String query, int did){

    // Build query vector
    Scanner s = new Scanner(query);
    Vector < String > qv = new Vector < String > ();
    while (s.hasNext()){
      String term = s.next();
      qv.add(term);
    }

    // Get the document vector. For hw1, you don't have to worry about the
    // details of how index works.
    Document d = _index.getDoc(did);
    Vector < String > dv = d.get_title_vector();

    // Score the document. Here we have provided a very simple ranking model,
    // where a document is scored 1.0 if it gets hit by at least one query term.
    double score = 0.0;
    for (int i = 0; i < dv.size(); ++i){
      for (int j = 0; j < qv.size(); ++j){
        if (dv.get(i).equals(qv.get(j))){
          score = 1.0;
          break;
        }
      }
    }

    return new ScoredDocument(did, d.get_title_string(), score);
  }
  
  public ScoredDocument runquery_tfidf(String query, int did){
	    // Build query hashmap
	    Scanner s = new Scanner(query).useDelimiter("\\+");
	    HashMap<String, Double> qv = new HashMap<String, Double>();
	    // Get the document vector. For hw1, you don't have to worry about the
	    // details of how index works.
	    Document d = _index.getDoc(did);
	    Vector < String > dv = d.get_title_vector();
	   	    
	  //first calculate the tf for query vector, stored in qv's value
	    while (s.hasNext()){
	      String term = s.next();
	      if(qv.containsKey(term)){
	    	  double oldtf = qv.get(term);
	    	  qv.put(term, oldtf+1.0);
	      }
	      else{
	    	  qv.put(term,1.0);
	      }
	    }    
	    //get normalized query term vector
	    HashMap<String, Double> qNorm = constructL2Vec(qv);
	     
	   //process document, get tf from doc body first
	   Vector<String> docBody = d.get_body_vector();
	   HashMap<String, Double> docv = new HashMap<String, Double>();
	   for(int i = 0;i < docBody.size();i++){
		   String term = docBody.get(i);
		   if(docv.containsKey(term)){
			   double oldtf = docv.get(term);
			   docv.put(term, oldtf+1.0);
		   }
		   else{
			   docv.put(term,1.0);
		   }
	   }
	   //get normalized doc term vector
	   HashMap<String,Double> docNorm = constructL2Vec(docv);
	   
	    // Score the document with cosine similarity
	    double score = cosine(qNorm,docNorm);
	    return new ScoredDocument(did, d.get_title_string(), score);	  
  }

  public ScoredDocument runquery_lm(String query, int did){
	  // Build query vector
	    Scanner s = new Scanner(query).useDelimiter("\\+");
	    Vector < String > qv = new Vector < String > ();
	    while (s.hasNext()){
	      String term = s.next();
	      qv.add(term);
	    }

	    // Get the document vector.
	    Document d = _index.getDoc(did);
	    Vector < String > dv = d.get_title_vector();
	  //process document, get tf from doc body
		Vector<String> docBody = d.get_body_vector();
	    HashMap<String, Double> docv = new HashMap<String, Double>();
		for(int i = 0;i < docBody.size();i++){
			String term = docBody.get(i);
			if(docv.containsKey(term)){
				double oldtf = docv.get(term);
				docv.put(term, oldtf+1.0);
			}
			else{
				docv.put(term,1.0);
			}
		}
		//calculate p(termi|D) for each term in D
		double lamda = 0.5;
		HashMap<String, Double> docp = new HashMap<String, Double>();
		Iterator<String> termIter = docv.keySet().iterator();
		while(termIter.hasNext()){
			String term = termIter.next();
			double fterm = docv.get(term);
			double p = (1-lamda)*fterm/(1.0*docBody.size())+lamda*(Document.termFrequency(term)*1.0)/(1.0*Document.termFrequency());
			docp.put(term, p);
		}
	    //calculate log P(Q|D)
		double score = 0.0;
		for(int i = 0;i < qv.size();i++){
			String qterm = qv.get(i);
			if(docp.containsKey(qterm)){
				score = score + Math.log(docp.get(qterm))/Math.log(2.0);
			}
			else{
				score = score + Math.log(lamda*Document.termFrequency(qterm)/(1.0*Document.termFrequency()))/Math.log(2.0);
			}
		}
	  return new ScoredDocument(did, d.get_title_string(), score);	  
  }
  
  public ScoredDocument runquery_phrase(String query, int did){

	    // Build query vector
	    Scanner s = new Scanner(query).useDelimiter("\\+");
	    Vector < String > qv = new Vector < String > ();
	    HashMap<String,Vector<Integer>> queryPos = new HashMap<String, Vector<Integer>>();
	    int pos = 0;
	    while (s.hasNext()){
	      String term = s.next();
	      qv.add(term);
	      if(!s.hasNext()){///skip the last word in query vector for queryPos
	    	  break;
	      }
	      if(queryPos.containsKey(term)){
	    	  queryPos.get(term).add(pos);
	      }
	      else{
	    	  Vector<Integer> posList = new Vector<Integer>();
	    	  posList.add(pos);
	    	  queryPos.put(term, posList);
	      }
	      pos++;
	    }

	    // Get the document vector
	    Document d = _index.getDoc(did);
	    Vector < String > dv = d.get_title_vector();
	    Vector<String> docBody = d.get_body_vector();
	    
	    int match = 0;
	    for(int i = 0; i < docBody.size();i++){
	    	String term = docBody.get(i);
	    	if(queryPos.containsKey(term)){
	    		for(int j = 0;j < queryPos.get(term).size();j++){
	    			int posQuery = queryPos.get(term).get(j);//position of current word in query
	    			if(docBody.get(i+1).equals(qv.get(posQuery+1))){
	    				match++;
	    				break;
	    			}
	    		}
	    	}
	    }
	    // Score the document. match*1.0
	    return new ScoredDocument(did, d.get_title_string(), match*1.0);
	  }  
  
  public ScoredDocument runquery_numview(String query, int did){  
	  // Get the document vector.
	    Document d = _index.getDoc(did);
	   
	    // Score the document with number of views
	    double score = d.get_numviews()*1.0;
	    return new ScoredDocument(did, d.get_title_string(), score);
	  }
  
private double cosine(HashMap<String, Double> qv, HashMap<String, Double> docv) {
	// calculate cosine similarity
	double sim = 0.0;
	if(qv.size()<=docv.size()){
		Iterator<String> termIter = qv.keySet().iterator();
		while(termIter.hasNext()){
			String term = termIter.next();
			if(docv.containsKey(term)){
				sim = sim + qv.get(term)*docv.get(term);
			}
		}
	}
	else{
		Iterator<String> termIter = docv.keySet().iterator();
		while(termIter.hasNext()){
			String term = termIter.next();
			if(qv.containsKey(term)){
				sim = sim + docv.get(term);
			}
		}
	}
	return sim;
}

private HashMap<String, Double> constructL2Vec(HashMap<String, Double> rawv) {
	// raws is the term vector, each element is tf
	double sqrSum = 0.0;
	 int numDoc = _index.numDocs();
	 HashMap<String,Double> tfidf = new HashMap<String,Double>();
	   Iterator<String> termItr = rawv.keySet().iterator();
	   while(termItr.hasNext()){
		   String term = termItr.next();
		   int dt = Document.documentFrequency(term);
		   double idf = 1.0+Math.log((numDoc*1.0)/(dt*1.0))/Math.log(2.0);
		   double tf = rawv.get(term);
		   tfidf.put(term, tf*idf);
		   sqrSum = sqrSum + tf*tf*idf*idf;
	   }
	   return l2Normlize(tfidf,sqrSum);//normalize term vector
	
}

private HashMap<String, Double> l2Normlize(HashMap<String, Double> termv, double sqrSum) {
	// TODO Auto-generated method stub
	HashMap<String,Double> normv = new HashMap<String,Double>();
	Iterator<String> termIter = termv.keySet().iterator();
	double sqrt = Math.sqrt(sqrSum);
	while(termIter.hasNext()){
		String term = termIter.next();
		double origin = termv.get(term);
		normv.put(term, origin/sqrt);
	}
	return normv;
}
  
}
