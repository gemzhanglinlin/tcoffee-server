package io.seq;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Reader;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import play.Logger;
import util.Utils;

/**
 * Read and validate a multiple sequence alignment file in Clustal W format 
 * 
 * @author Paolo Di Tommaso
 *
 */
public class Clustal extends AbstractFormat {
	
	static final Pattern HEADER = Pattern.compile("^CLUSTAL W \\([^\\)]+\\).*$");

	final Pattern rowPattern;
	
	/** contains the clustal header declaration */
	String header;
	
	/** all the sequence block read */
	List<Block> blocks = new ArrayList<Block>();
	
	public Clustal() { 
		this(Alphabet.AminoAcid.INSTANCE);
	}
	
	public  Clustal(Alphabet alpha) {
		super(alpha);
		
		StringBuilder sPattern = new StringBuilder();
		sPattern.append("(\\S+)\\s+([");
		for( char ch : alpha.letters() ) { 
			/* the alphabet coudl include some special chars, do not include them */
			if( ch >= 'a' && ch <= 'z' || (ch >= 'A' && ch <= 'Z')) { 
				sPattern.append(ch);
			}
		}
		sPattern.append("\\-]+)(?: (\\d+))?");
		rowPattern = Pattern.compile(sPattern.toString());
	}

	@Override
	void parse(Reader reader) throws IOException {
		
		/* the read keys order as in the file */
		String[] keylist = null;
		Map<String,StringBuilder> builder = new HashMap<String,StringBuilder>();
		
		
		BufferedReader input = null;
		try { 
			 input = new BufferedReader(reader);
			 
			/* 
			 * the first row have to be the header 
			 */
			this.header = parseHeader(input.readLine());
			
			Block block;
			while( (block=parseBlock(input)) != null ) { 

				blocks.add(block);
				
				/* validate the block and contruct the result sequences */
				if( keylist ==null ) { 
					keylist  = block.getKeysList().toArray(new String[]{});
					for( String key : keylist  ) { 
						builder.put(key, new StringBuilder());
					}
				}

				for( int i=0, c=block.list.size(); i<c; i++ ) { 
					Fragment fragment = block.list.get(i);
					/* check that the order in all the blocks match */
					if( !Utils.isEquals(keylist [i], fragment.key) ) { 
						throw new FormatParseException("Clustal content wrongly formatted");
					}
					
					builder.get(fragment.key).append( fragment.value ); 
				}
			}

			
			/* 
			 * final loop on the result object to create the list of sequences 
			 */
			List<ClustalSequence> result = new ArrayList<Clustal.ClustalSequence>(keylist.length);
			for( String key : keylist ) {
				StringBuilder val = builder.get(key);
				ClustalSequence seq = new ClustalSequence(key, val.toString());
				result.add(seq);
			}
			
			this.sequences = result;
			
		}
		catch( FormatParseException e ) { 
			this.error = e.getMessage();
		}
		finally { 
			if( input != null ) try { input.close(); } catch( Exception e ) { Logger.warn(e,"Error closing reader"); }
		}
		
	}

	/** 
	 * Parse a Clustal formatted header file 
	 * 
	 * @param line
	 * @return the file header line
	 */
	String parseHeader(String line) {

		if( Utils.isNotEmpty(line)) { 
			Matcher matcher = HEADER.matcher(line);
			if( matcher.matches() ) { 
				return line;
			}
		}
		
		throw new FormatParseException("Missing Clustal header declaration");
	}
	
	/**
	 * Parse a Clustal formatted file block 
	 * 
	 * @param reader
	 * @return a {@link Block} instance 
	 * @throws IOException 
	 */
	Block parseBlock( Reader reader ) throws IOException { 
	
		BufferedReader input = reader instanceof BufferedReader 
						? (BufferedReader)reader 
						: new BufferedReader(reader); 
		
		String line;
		while( (line=input.readLine()) != null && "".equals(line.trim()) ) { 
			// consume empty lines 
		}
		
		Block result = new Block();
		while( line != null ) { 
			Matcher matcher = rowPattern.matcher(line);
			if(matcher.matches()) { 
				String key = matcher.group(1);
				String value = matcher.group(2);
				result.add(key, value);
			}
			else if( isBlockTermination(line) ) { 
				break;
			}
			else { 
				throw new FormatParseException("Invalid Clustal format in line: '%s'", line);
			}
			
			line = input.readLine();
		}
		
		return result.list.size()>0 ? result : null;
	}
	
	/**
	 * Return the parsed Clustal sequences as a string 
	 */
	public String toString() { 
		StringBuilder result = new StringBuilder();
		result.append(header);
		result.append("\n");
		
		for( Block blk : blocks ) { 
			result.append("\n");
			result.append(blk.toString());
		}
		
		return result.toString();
	}
	
	/**
	 * @param line a row in a Clustal formatted file
	 * @return <code>true</code> if the line is a block separator
	 */
	static boolean isBlockTermination(String line) {
		return line.trim().equals("") || line.contains("*") || line.contains(".") || line.contains(":");
	}


	/**
	 * @param file the file to be checked 
	 * @return <code>true</code> if the specified file a valid content in FASTA format 
	 */
	public static boolean isValid(File file, Alphabet alphabet) {
		try {
			Clustal clustal = new Clustal(alphabet);
			clustal.parse(file);
			return clustal.isValid();
		} 
		catch (FileNotFoundException e) {
			Logger.warn("Specified file does not exists: %s", file);
			return false;
		}
	}

	public static boolean isValid(String sequences, Alphabet alphabet) {
		Clustal clustal = new Clustal(alphabet);
		clustal.parse(sequences);
		return clustal.isValid();
	}	
	
	/*
	 * A contiguous block in a Clustal file
	 *
	 */
	static class Block implements Serializable { 
		int keyMaxLength;
		List<Fragment> list = new ArrayList<Fragment>();
		
		public Fragment add( String key, String value) { 
			if( key.length()>keyMaxLength ) { 
				keyMaxLength = key.length();
			}
			
			Fragment fragment = new Fragment();
			fragment.key = key;
			fragment.value = value;
			list.add(fragment);
			return fragment;
		}
		
		@Override
		public String toString() { 
			StringBuilder result = new StringBuilder();
			for( Fragment frag : list ) { 
				result.append( frag.key );
				for( int i=0, c=keyMaxLength-frag.key.length(); i<c; i++ ) { 
					result.append(" ");
				}
				result.append("  "); // <-- look adding TWO blank here as separator between the key and the sequence
				result.append( frag.value );
				result.append("\n");
			}
			
			return result.toString();
		}
		
		public List<String> getKeysList() { 
			List<String> result = new ArrayList<String>();
			for( Fragment frag : list ) { 
				result.add( frag.key );
			}
			
			return result;
		}
	}
	
	/*
	 * A entry of a clustal block e.g. the proteing key and the sequence fragment 
	 */
	static class Fragment implements Serializable { 
		String key;
		String value;
	}
	
	static class ClustalSequence extends Sequence { 
		
		public ClustalSequence( String key, String val ) { 
			this.header = key;
			this.value = val != null ? val.replace("-", "") : null;
		}
	}

}