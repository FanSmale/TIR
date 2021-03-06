package algorithm;

import java.io.*;
import java.util.Arrays;

import common.SimpleTools;
import datamodel.RatingSystem2DBoolean;

/**
 * The main algorithm for three-way conversational recommendation. <br>
 * Project: Three-way conversational recommendation.<br>
 * 
 * @author Fan Min<br>
 *         www.fansmale.com, github.com/fansmale/TCR.<br>
 *         Email: minfan@swpu.edu.cn, minfanphd@163.com.<br>
 * @date Created: December 3, 2019.<br>
 *       Last modified: January 30, 2020.
 * @version 1.0
 */

public class TCR {

	/**
	 * The dataset.
	 */
	RatingSystem2DBoolean dataset;

	/**
	 * Number of users.
	 */
	protected int numUsers;

	/**
	 * Number of items.
	 */
	protected int numItems;

	/**
	 * Number of ratings.
	 */
	protected int numRatings;

	/**
	 * The cost matrix.
	 */
	double[][] costMatrix;

	/**
	 * The statistics information (NN, NP, BN, ...) for current user.
	 */
	int[][] recommendationStatistics = new int[3][2];

	/**
	 * The total cost.
	 */
	double totalCost;

	/**
	 * Stage 1 recommender.
	 */
	public PopularityBasedRecommendation stage1Recommender;

	/**
	 * Stage 1 recommender.
	 */
	public MF2DBooleanIncremental stage2Recommender;

	/**
	 *********************************** 
	 * The constructor.
	 * 
	 * @param paraFilename
	 *            The rating filename.
	 * @param paraNumUsers
	 *            The number of users.
	 * @param paraNumItems
	 *            The number of items.
	 * @param paraNumRatings
	 *            The number of ratings.
	 * @param paraRatingLowerBound
	 *            The lower bound of ratings.
	 * @param paraRatingUpperBound
	 *            The upper bound of ratings.
	 * @param paraLikeThrehold
	 *            The threshold for like.
	 * @param paraCompress
	 *            Is the data in compress format?
	 * @param paraDataTransformAlgorithm
	 *            The data transform algorithm.
	 *********************************** 
	 */
	public TCR(String paraFilename, int paraNumUsers, int paraNumItems, int paraNumRatings,
			double paraRatingLowerBound, double paraRatingUpperBound, double paraLikeThreshold,
			boolean paraCompress, int paraDataTransformAlgorithm, double paraGLTranformV) {
		dataset = new RatingSystem2DBoolean(paraFilename, paraNumUsers, paraNumItems,
				paraNumRatings, paraRatingLowerBound, paraRatingUpperBound, paraLikeThreshold,
				paraCompress);

		stage1Recommender = new PopularityBasedRecommendation(dataset);
		stage1Recommender.setPopularityThresholds(new double[] { 0.3, 0.7 });

		if (paraDataTransformAlgorithm == 0) {
			stage2Recommender = new MF2DBooleanIncremental(dataset);
		} else {
			stage2Recommender = new MF2DBooleanIncrementalGLG(dataset,
					paraDataTransformAlgorithm - 1, paraGLTranformV);
		} // Of if

		// Step 3. Initialize.
		initialize();
	}// Of the constructor

	/**
	 *********************************** 
	 * Initialize.
	 *********************************** 
	 */
	public void initialize() {
		numUsers = dataset.getNumUsers();
		numItems = dataset.getNumItems();
		numRatings = dataset.getNumRatings();

		costMatrix = new double[3][2];
		costMatrix[0][0] = 2; // NN
		costMatrix[0][1] = 40; // NP
		costMatrix[1][0] = 20; // BN
		costMatrix[1][1] = 10; // BP
		costMatrix[2][0] = 50; // PN
		costMatrix[2][1] = 6; // PP
	}// Of initialize

	/**
	 *********************************** 
	 * Initialize.
	 *********************************** 
	 */
	public void reset() {
		for (int i = 0; i < recommendationStatistics.length; i++) {
			for (int j = 0; j < recommendationStatistics[i].length; j++) {
				recommendationStatistics[i][j] = 0;
			} // Of for j
		} // Of for i
	}// Of reset

	/**
	 *********************************** 
	 * Getter.
	 *********************************** 
	 */
	public int[][] getRecommendationStatistics() {
		return recommendationStatistics;
	}// Of getRecommendationStatistics

	/**
	 *************************** 
	 * Setter.
	 *************************** 
	 */
	public void setCostMatrix(double[][] paraCostMatrix) {
		costMatrix = paraCostMatrix;
	}// Of setCostMatrix

	/**
	 *********************************** 
	 * Getter.
	 *********************************** 
	 */
	public double getTotalCost() {
		return totalCost;
	}// Of getTotalCost

	/**
	 *********************************** 
	 * Setter.
	 *********************************** 
	 */
	public void setCostMatrix(int paraNN, int paraNP, int paraBN, int paraBP, int paraPN,
			int paraPP) {
		costMatrix[0][0] = paraNN;
		costMatrix[0][1] = paraNP;
		costMatrix[1][0] = paraBN;
		costMatrix[1][1] = paraBP;
		costMatrix[2][0] = paraPN;
		costMatrix[2][1] = paraPP;
		// Q.
	}// Of setCostMatrix

	/**
	 *********************************** 
	 * Compute the total cost for a user.
	 * 
	 * @param paraUser
	 *            The index of the given user.
	 * @param paraRecommendations
	 *            The recommendations to the user, true for recommendation.
	 * @param paraPromotions
	 *            The promotions for the user, true for promotion.
	 * @return The total cost for the user.
	 *********************************** 
	 */
	public double computeTotalCostForUser(int paraUser) {
		boolean[] tempRecommendations = UserBasedThreeWayRecommender.currentUserRecommendations;
		boolean[] tempPromotions = UserBasedThreeWayRecommender.currentUserPromotions;
		return computeTotalCostForUser(paraUser, tempRecommendations, tempPromotions);
	}// Of computeTotalCostForUser

	/**
	 *********************************** 
	 * Compute the total cost for a user.
	 * 
	 * @param paraUser
	 *            The index of the given user.
	 * @param paraRecommendations
	 *            The recommendations to the user, true for recommendation.
	 * @param paraPromotions
	 *            The promotions for the user, true for promotion.
	 * @return The total cost for the user.
	 *********************************** 
	 */
	public double computeTotalCostForUser(int paraUser, boolean[] paraRecommendations,
			boolean[] paraPromotions) {

		// Step 1. Check them.
		int tempUserNumRatings = dataset.getUserNumRatings(paraUser);
		double resultTotalCost = 0;
		int tempBehavior;
		int tempLike;
		for (int i = 0; i < tempUserNumRatings; i++) {
			if (paraRecommendations[dataset.getTriple(paraUser, i).item]) {
				tempBehavior = UserBasedThreeWayRecommender.RECOMMEND;
			} else if (paraPromotions[dataset.getTriple(paraUser, i).item]) {
				tempBehavior = UserBasedThreeWayRecommender.PROMOTE;
			} else {
				tempBehavior = UserBasedThreeWayRecommender.NON_RECOMMEND;
			} // Of if

			tempLike = 0;// 0 means "dislike".
			if (dataset.getTriple(paraUser, i).rating > dataset.getLikeThreshold()) {
				tempLike = 1;// 1 means "like".
			} // Of if

			// System.out.println(
			// "" + dataset.getTriple(paraUser, i) + " vs. likeThreshold " +
			// tempActualThreshold);

			resultTotalCost += costMatrix[tempBehavior][tempLike];
		} // Of for i

		return resultTotalCost;
	}// Of computeTotalCostForUser

	/**
	 *********************************** 
	 * Compute recommendation statistics for a user.
	 * 
	 * @param paraUser
	 *            The index of the given user.
	 * @return The recommendation statistics for the user.
	 *********************************** 
	 */
	public double computeTotalCost() {
		totalCost = 0;
		for (int i = 0; i < recommendationStatistics.length; i++) {
			for (int j = 0; j < recommendationStatistics[0].length; j++) {
				totalCost += costMatrix[i][j] * recommendationStatistics[i][j];
			} // Of for j
		} // Of for i

		return totalCost;
	}// Of computeTotalCost

	/**
	 *********************************** 
	 * Compute recommendation statistics for a user.
	 * 
	 * @param paraUser
	 *            The index of the given user.
	 * @return The recommendation statistics for the user.
	 *********************************** 
	 */
	public int[][] computeUserRecommendationStatistics(int paraUser) {
		int[][] resultUserRecommendationStatistics = new int[3][2];

		// Step 1. Check them.
		int tempUserNumRatings = dataset.getUserNumRatings(paraUser);
		int tempBehavior;
		int tempLike;
		for (int i = 0; i < tempUserNumRatings; i++) {
			if (UserBasedThreeWayRecommender.currentUserRecommendations[dataset.getTriple(paraUser, i).item]) {
				tempBehavior = UserBasedThreeWayRecommender.RECOMMEND;
			} else if (UserBasedThreeWayRecommender.currentUserPromotions[dataset.getTriple(paraUser, i).item]) {
				tempBehavior = UserBasedThreeWayRecommender.PROMOTE;
			} else {
				tempBehavior = UserBasedThreeWayRecommender.NON_RECOMMEND;
			} // Of if

			tempLike = 0;// 0 means "dislike".
			if (dataset.getTriple(paraUser, i).rating > dataset.getLikeThreshold()) {
				tempLike = 1;// 1 means "like".
			} // Of if

			resultUserRecommendationStatistics[tempBehavior][tempLike]++;
		} // Of for i

		return resultUserRecommendationStatistics;
	}// Of computeUserRecommendationStatistics

	/**
	 *********************************** 
	 * Leave-user-out recommendation.
	 * 
	 * @return The total cost of the current user.
	 *********************************** 
	 */
	public double leaveUserOutRecommend() {
		double resultTotalCost = 0;
		for (int i = 0; i < numUsers; i++) {
			if (i % 100 == 0) {
				SimpleTools.processTrackingOutput("Recommending for user #" + i + ":");
			} // Of if

			resultTotalCost += recommendForUser(i);
		} // Of for i

		// computeRecommendationStatitics();
		// computeTotalCost();

		return resultTotalCost;
	}// Of leaveUserOutRecommend

	/**
	 *********************************** 
	 * Recommend for one user.
	 * 
	 * @param paraUser
	 *            The user index.
	 * @return The total cost of the current user.
	 *********************************** 
	 */
	public double recommendForUser(int paraUser) {
		SimpleTools.processTrackingOutput("\r\nUser " + paraUser);
		// Step 1. Initialize
		double resultTotalCost;
		
		// Step 2. Popularity-based recommendation.
		stage1Recommender.recommendForUser(paraUser);
		//boolean[][] tempRecommendationMatrix = stage1Recommender.recommendForUser(paraUser);
		//currentUserRecommendations = tempRecommendationMatrix[0];
		//currentUserPromotions = tempRecommendationMatrix[1];

		// stage1Recommender.threeWayRecommend(paraUser,
		// currentUserRecommendations,
		// currentUserPromotions);
		resultTotalCost = computeTotalCostForUser(paraUser);
		SimpleTools.variableTrackingOutput("User " + paraUser
				+ ", after popularity based recommendation, total cost = " + resultTotalCost);

		// Step 3. MF based recommendation.
		stage2Recommender.recommendForUser(paraUser);

		resultTotalCost = computeTotalCostForUser(paraUser);
		SimpleTools.variableTrackingOutput("User " + paraUser
				+ ", after MF based recommendation, total cost = " + resultTotalCost);
		// System.out.print("Finally, the cost of user " + paraUser + " is: " +
		// resultTotalCost);
		// System.out.println();

		// Step 4. Update statistics
		int tempUserNumRatings = dataset.getUserNumRatings(paraUser);
		int tempBehavior;
		int tempLike;
		for (int i = 0; i < tempUserNumRatings; i++) {
			if (UserBasedThreeWayRecommender.currentUserRecommendations[dataset.getTriple(paraUser, i).item]) {
				tempBehavior = UserBasedThreeWayRecommender.RECOMMEND;
			} else if (UserBasedThreeWayRecommender.currentUserPromotions[dataset.getTriple(paraUser, i).item]) {
				tempBehavior = UserBasedThreeWayRecommender.PROMOTE;
			} else {
				tempBehavior = UserBasedThreeWayRecommender.NON_RECOMMEND;
			} // Of if

			tempLike = 0;// 0 means "dislike".
			if (dataset.getTriple(paraUser, i).rating > dataset.getLikeThreshold()) {
				tempLike = 1;// 1 means "like".
			} // Of if

			recommendationStatistics[tempBehavior][tempLike]++;
		} // Of for i

		return resultTotalCost;
	}// Of recommendForUser

	/**
	 *********************************** 
	 * The main entrance.
	 * 
	 * @throws IOException
	 * @throws NumberFormatException
	 *********************************** 
	 */
	public static void main(String args[]) {
		// TIR2 tir = new TIR2("data/movielens100k.data", 943, 1682, 100000,
		// -10, 10);
		SimpleTools.processTracking = true;

		TCR tcr = new TCR("data/jester-data-1/jester-data-1.txt", 24983, 101, 1810455, -10, 10, 0.5,
				false, 2, 1.0);
		System.out.println(tcr);
		tcr.stage2Recommender.pretrain();

		double tempTotalCost = tcr.leaveUserOutRecommend();

		System.out.println("The total cost for all users is: " + tempTotalCost);
	}// Of main

}// Of class TCR
