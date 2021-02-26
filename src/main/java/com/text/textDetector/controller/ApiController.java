package com.text.textDetector.controller;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.api.gax.longrunning.OperationFuture;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.Bucket;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.Storage.BlobListOption;
import com.google.cloud.storage.StorageOptions;
import com.google.cloud.vision.v1.AnnotateFileResponse;
import com.google.cloud.vision.v1.AnnotateFileResponse.Builder;
import com.google.cloud.vision.v1.AnnotateImageResponse;
import com.google.cloud.vision.v1.AsyncAnnotateFileRequest;
import com.google.cloud.vision.v1.AsyncAnnotateFileResponse;
import com.google.cloud.vision.v1.AsyncBatchAnnotateFilesResponse;
import com.google.cloud.vision.v1.Feature;
import com.google.cloud.vision.v1.GcsDestination;
import com.google.cloud.vision.v1.GcsSource;
import com.google.cloud.vision.v1.ImageAnnotatorClient;
import com.google.cloud.vision.v1.ImageAnnotatorSettings;
import com.google.cloud.vision.v1.InputConfig;
import com.google.cloud.vision.v1.OperationMetadata;
import com.google.cloud.vision.v1.OutputConfig;
import com.google.common.collect.Lists;
import com.google.protobuf.util.JsonFormat;
import com.text.textDetector.model.InputRequest;

@RestController
public class ApiController {

	

	@RequestMapping(value = "/fetchTextFromFile", method = { RequestMethod.POST })
	public String detectDocumentTextGcs(@RequestBody InputRequest apiRequest) throws IOException, InterruptedException, ExecutionException, TimeoutException {
		
String gcsPath = "gs://trailblazer_images/".concat(apiRequest.getFileName());
String gcsDestinationPath = "gs://trailblazer_images/";

//		GoogleCredentials credentials = GoogleCredentials.fromStream(new FileInputStream("C:\\Users\\Sahil Aggarwal\\Desktop\\textDetector\\textDetector\\src\\main\\resources\\My First Project-5fcc280ffe7a.json"))
//				.createScoped(Lists.newArrayList("https://www.googleapis.com/auth/cloud-platform"));
		

		
//		ImageAnnotatorSettings imageAnnotatorSettings =
//			     ImageAnnotatorSettings.newBuilder()
//			         .setCredentialsProvider(FixedCredentialsProvider.create(credentials))
//			         .build();

		
		// Initialize client that will be used to send requests. This client only needs to be created
	    // once, and can be reused for multiple requests. After completing all of your requests, call
	    // the "close" method on the client to safely clean up any remaining background resources.
//	    try (ImageAnnotatorClient client = ImageAnnotatorClient.create(imageAnnotatorSettings)) {
	    try (ImageAnnotatorClient client = ImageAnnotatorClient.create()) {
	      List<AsyncAnnotateFileRequest> requests = new ArrayList<>();

	      // Set the GCS source path for the remote file.
	      GcsSource gcsSource = GcsSource.newBuilder().setUri(gcsPath).build();

	      // Create the configuration with the specified MIME (Multipurpose Internet Mail Extensions)
	      // types
	      InputConfig inputConfig =
	          InputConfig.newBuilder()
	              .setMimeType(
	                  "application/pdf") // Supported MimeTypes: "application/pdf", "image/tiff"
	              .setGcsSource(gcsSource)
	              .build();

	      // Set the GCS destination path for where to save the results.
	      GcsDestination gcsDestination =
	          GcsDestination.newBuilder().setUri(gcsDestinationPath).build();

	      // Create the configuration for the System.output with the batch size.
	      // The batch size sets how many pages should be grouped into each json System.output file.
	      OutputConfig outputConfig =
	          OutputConfig.newBuilder().setBatchSize(2).setGcsDestination(gcsDestination).build();

	      // Select the Feature required by the vision API
	      Feature feature = Feature.newBuilder().setType(Feature.Type.DOCUMENT_TEXT_DETECTION).build();

	      // Build the OCR request
	      AsyncAnnotateFileRequest request =
	          AsyncAnnotateFileRequest.newBuilder()
	              .addFeatures(feature)
	              .setInputConfig(inputConfig)
	              .setOutputConfig(outputConfig)
	              .build();

	      requests.add(request);

	      // Perform the OCR request
	      OperationFuture<AsyncBatchAnnotateFilesResponse, OperationMetadata> response =
	          client.asyncBatchAnnotateFilesAsync(requests);

	      System.out.println("Waiting for the operation to finish.");

	      // Wait for the request to finish. (The result is not used, since the API saves the result to
	      // the specified location on GCS.)
	      List<AsyncAnnotateFileResponse> result =
	          response.get(180, TimeUnit.SECONDS).getResponsesList();

	      // Once the request has completed and the System.output has been
	      // written to GCS, we can list all the System.output files.
//	      Storage storage = StorageOptions.newBuilder().setCredentials(credentials).build().getService();
	      Storage storage = StorageOptions.getDefaultInstance().getService();

	      // Get the destination location from the gcsDestinationPath
	      Pattern pattern = Pattern.compile("gs://([^/]+)/(.+)");
	      Matcher matcher = pattern.matcher(gcsDestinationPath.concat("output-1-to-1.json"));

	      if (matcher.find()) {
	        String bucketName = matcher.group(1);
	        String prefix = matcher.group(2);

	        // Get the list of objects with the given prefix from the GCS bucket
	        Bucket bucket = storage.get(bucketName);
	        com.google.api.gax.paging.Page<Blob> pageList = bucket.list(BlobListOption.prefix(prefix));

	        Blob firstOutputFile = null;

	        // List objects with the given prefix.
	        System.out.println("Output files:");
	        for (Blob blob : pageList.iterateAll()) {
	          System.out.println(blob.getName());

	          // Process the first System.output file from GCS.
	          // Since we specified batch size = 2, the first response contains
	          // the first two pages of the input file.
	          if (firstOutputFile == null) {
	            firstOutputFile = blob;
	          }
	        }

	        // Get the contents of the file and convert the JSON contents to an AnnotateFileResponse
	        // object. If the Blob is small read all its content in one request
	        // (Note: the file is a .json file)
	        // Storage guide: https://cloud.google.com/storage/docs/downloading-objects
	        String jsonContents = new String(firstOutputFile.getContent());
	        System.out.println("---"+jsonContents);
	        Builder builder = AnnotateFileResponse.newBuilder();
	        JsonFormat.parser().merge(jsonContents, builder);

	        // Build the AnnotateFileResponse object
	        AnnotateFileResponse annotateFileResponse = builder.build();

	        // Parse through the object to get the actual response for the first page of the input file.
	        AnnotateImageResponse annotateImageResponse = annotateFileResponse.getResponses(0);

	        // Here we print the full text from the first page.
	        // The response contains more information:
	        // annotation/pages/blocks/paragraphs/words/symbols
	        // including confidence score and bounding boxes
	        System.out.format("%nText: %s%n", annotateImageResponse.getFullTextAnnotation().getText());
	        return annotateImageResponse.getFullTextAnnotation().getText();
	      } else {
	        System.out.println("No MATCH");
	        return  "";
	      }
	    }
	}
}
