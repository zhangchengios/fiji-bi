package fiji.plugin.trackmate.features.track;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;

import org.jgrapht.graph.DefaultWeightedEdge;

import net.imglib2.algorithm.Benchmark;
import net.imglib2.algorithm.MultiThreaded;
import net.imglib2.multithreading.SimpleMultiThreading;
import fiji.plugin.trackmate.Dimension;
import fiji.plugin.trackmate.FeatureModel;
import fiji.plugin.trackmate.Spot;
import fiji.plugin.trackmate.TrackMateModel;
import fiji.plugin.trackmate.TrackMateModelChangeEvent;

public class TrackLocationAnalyzer implements TrackFeatureAnalyzer, MultiThreaded, Benchmark {

	/*
	 * FEATURE NAMES 
	 */
	public static final String KEY = "Track location";
	public static final String TRACK_ID = "TRACK_ID";
	public static final String X_LOCATION = "X_LOCATION";
	public static final String Y_LOCATION = "Y_LOCATION";
	public static final String Z_LOCATION = "Z_LOCATION";

	public static final List<String> FEATURES = new ArrayList<String>(4);
	public static final Map<String, String> FEATURE_NAMES = new HashMap<String, String>(4);
	public static final Map<String, String> FEATURE_SHORT_NAMES = new HashMap<String, String>(4);
	public static final Map<String, Dimension> FEATURE_DIMENSIONS = new HashMap<String, Dimension>(4);

	static {
		FEATURES.add(X_LOCATION);
		FEATURES.add(Y_LOCATION);
		FEATURES.add(Z_LOCATION);
		FEATURES.add(TRACK_ID);

		FEATURE_NAMES.put(TRACK_ID, "Track ID");
		FEATURE_NAMES.put(X_LOCATION, "X Location (mean)");
		FEATURE_NAMES.put(Y_LOCATION, "Y Location (mean)");
		FEATURE_NAMES.put(Z_LOCATION, "Z Location (mean)");

		FEATURE_SHORT_NAMES.put(TRACK_ID, "ID");
		FEATURE_SHORT_NAMES.put(X_LOCATION, "X");
		FEATURE_SHORT_NAMES.put(Y_LOCATION, "Y");
		FEATURE_SHORT_NAMES.put(Z_LOCATION, "Z");

		FEATURE_DIMENSIONS.put(TRACK_ID, Dimension.NONE);
		FEATURE_DIMENSIONS.put(X_LOCATION, Dimension.POSITION);
		FEATURE_DIMENSIONS.put(Y_LOCATION, Dimension.POSITION);
		FEATURE_DIMENSIONS.put(Z_LOCATION, Dimension.POSITION);
	}

	private int numThreads;
	private long processingTime;
	private final TrackMateModel model;

	/*
	 * CONSTRUCTOR
	 */

	public TrackLocationAnalyzer(final TrackMateModel model) {
		this.model = model;
		setNumThreads();
	}


	@Override
	public void process(final Collection<Integer> trackIDs) {

		final ArrayBlockingQueue<Integer> queue = new ArrayBlockingQueue<Integer>(trackIDs.size(), false, trackIDs);
		final FeatureModel fm = model.getFeatureModel();

		Thread[] threads = SimpleMultiThreading.newThreads(numThreads);
		for (int i = 0; i < threads.length; i++) {
			threads[i] = new Thread("TrackLocationAnalyzer thread " + i) {
				@Override
				public void run() {
					Integer trackID;
					while ((trackID = queue.poll()) != null) {

						Set<Spot> track = model.getTrackModel().getTrackSpots(trackID);

						double x = 0;
						double y = 0;
						double z = 0;

						for(Spot spot : track) {
							x += spot.getFeature(Spot.POSITION_X);
							y += spot.getFeature(Spot.POSITION_Y);
							z += spot.getFeature(Spot.POSITION_Z);
						}
						int nspots = track.size();
						x /= nspots;
						y /= nspots;
						z /= nspots;

						fm.putTrackFeature(trackID, TRACK_ID, Double.valueOf(trackID));
						fm.putTrackFeature(trackID, X_LOCATION, x);
						fm.putTrackFeature(trackID, Y_LOCATION, y);
						fm.putTrackFeature(trackID, Z_LOCATION, z);

					}

				}
			};
		}

		long start = System.currentTimeMillis();
		SimpleMultiThreading.startAndJoin(threads);
		long end = System.currentTimeMillis();
		processingTime = end - start;
	}


	@Override
	public void modelChanged(TrackMateModelChangeEvent event) {

		// We are affected only by edge changes
		if (event.getEventID() == TrackMateModelChangeEvent.MODEL_MODIFIED) {

			Set<DefaultWeightedEdge> edges = event.getEdges();
			if (edges.isEmpty()) {
				return;
			}
			
			System.out.println(edges);

			// Collect track IDs
			Set<Integer> targetIDs = new HashSet<Integer>(edges.size());
			for (DefaultWeightedEdge edge : edges) {
				if (event.getEdgeFlag(edge).equals(TrackMateModelChangeEvent.FLAG_EDGE_REMOVED)) {
					targetIDs.addAll(event.getNewTracksFor(edge));
				} else {
					targetIDs.add( model.getTrackModel().getTrackIDOf(edge) );
				}
			}
			
			System.out.println(targetIDs);

			// Recompute
			process(targetIDs);
		}
	}

	@Override
	public int getNumThreads() {
		return numThreads;
	}

	@Override
	public void setNumThreads() {
		this.numThreads = Runtime.getRuntime().availableProcessors();  
	}

	@Override
	public void setNumThreads(int numThreads) {
		this.numThreads = numThreads;

	}

	@Override
	public long getProcessingTime() {
		return processingTime;
	};

	@Override
	public String toString() {
		return KEY;
	}
}
