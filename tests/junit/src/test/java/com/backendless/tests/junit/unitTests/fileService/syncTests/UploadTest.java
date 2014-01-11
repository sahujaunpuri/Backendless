package com.backendless.tests.junit.unitTests.fileService.syncTests;

import com.backendless.Backendless;
import com.backendless.files.BackendlessFile;
import com.backendless.tests.junit.Defaults;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;

public class UploadTest extends TestsFrame
{
  @Test
  public void testUploadSingleFile() throws Throwable
  {
    File fileToUpload = null;
    File uploadedFile = null;

    try
    {
      fileToUpload = createRandomFile();
      String path = getRandomPath() + "/" + getRandomPath();

      BackendlessFile backendlessFile = Backendless.Files.upload( fileToUpload, path );
      Assert.assertNotNull( "Server returned a null", backendlessFile );
      Assert.assertNotNull( "Server returned a null url", backendlessFile.getFileURL() );
      Assert.assertEquals( "Server returned wrong url " + backendlessFile.getFileURL(), "https://api.backendless.com/" + Defaults.TEST_APP_ID.toLowerCase() + "/" + Defaults.TEST_VERSION.toLowerCase() + "/files/" + path + "/" + fileToUpload.getName(), backendlessFile.getFileURL() );
      uploadedFile = new File( Defaults.REPO_DIR + "/" + Defaults.TEST_APP_ID.toLowerCase() + "/" + path+ "/" + fileToUpload.getName() );

      Assert.assertTrue( "Server didn't create a file at the expected repo path: " + uploadedFile.getAbsolutePath(), uploadedFile.exists() && uploadedFile.isFile() );
      Assert.assertTrue( "File to upload and uploaded files doesn't have the same content", compareFiles( fileToUpload, uploadedFile ) );
    }
    finally
    {
      deleteFile( fileToUpload );
      deleteFile( uploadedFile );
    }
  }

  @Test
  public void testUploadInvalidPath() throws Throwable
  {
    File fileToUpload = createRandomFile();
    String path = "9!@%^&*(){}[]/?|`~";
    BackendlessFile backendlessFile = Backendless.Files.upload( fileToUpload, path );
    Assert.assertNotNull( "Server returned null result", backendlessFile );
    String expected = "https://api.backendless.com/" + Defaults.TEST_APP_ID.toLowerCase() + "/" + Defaults.TEST_VERSION.toLowerCase() + "/files/" + path + "/" + fileToUpload.getName();
    Assert.assertEquals( "Server returned wrong file url", expected, backendlessFile.getFileURL() );
    deleteFile( fileToUpload );
  }
}