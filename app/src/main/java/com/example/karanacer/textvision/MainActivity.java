package com.example.karanacer.textvision;

import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.method.Touch;
import android.util.SparseArray;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.vision.Frame;
import com.google.android.gms.vision.text.TextBlock;
import com.google.android.gms.vision.text.TextRecognizer;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.InputMismatchException;

public class MainActivity extends AppCompatActivity {

    Button mButton = null;
    ImageView mImageView = null;
    TextView mTextView = null;
    String imageText = "";
    Button mSaveButton = null;
    Button mSelectPhoto = null;
    //private Bitmap mImageBitmap = null;
    static final int REQUEST_IMAGE_CAPTURE = 1;
    private static final int REQUEST_FOR_IMAGE = 123;
    String mCurrentPhotoPath;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mButton = (Button)findViewById(R.id.takePhoto);
        mSaveButton = (Button) findViewById(R.id.saveToFile);
        mSaveButton.setEnabled(false);
        mSelectPhoto = (Button)findViewById(R.id.selectPhoto);
        mButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                dispatchTakePictureIntent();

            }
        });

        mSaveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveToTextFile();
            }
        });

        mSelectPhoto.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dispatchSelectImageIntent();
            }
        });

    }
    @Override
    protected void onDestroy(){
        super.onDestroy();
        if(!isChangingConfigurations()){
            File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
            deleteTempFiles(storageDir);
        }
    }

    private boolean deleteTempFiles(File file){
        if(file.isDirectory()){
            File[] files = file.listFiles();
            if(files != null){
                for(File f:files){
                    if(f.isDirectory()){
                        deleteTempFiles(f);
                    }
                    else{
                        f.delete();
                    }
                }
            }
        }
        return true;
    }

    private void dispatchTakePictureIntent(){
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        //Ensure's there is a camera activity to handle the intent
        if(takePictureIntent.resolveActivity(getPackageManager()) !=null){
            //Create a file for photo to go
            File photoFile = null;
            try {
                photoFile = createImageFile();  //creates a photo file and sets its directory
            }
            catch(IOException ex){
                Toast.makeText(this, "Failed to create Image File", Toast.LENGTH_SHORT).show();
            }
            if(photoFile != null) {
                Uri photoURI = FileProvider.getUriForFile(this,
                        "com.example.karanacer.textvision",
                        photoFile);
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
                startActivityForResult(takePictureIntent,REQUEST_IMAGE_CAPTURE);
            }
        }
    }

    private void dispatchSelectImageIntent(){
        Intent selectPictureIntent = new Intent(Intent.ACTION_GET_CONTENT);
        selectPictureIntent.setType("image/*");
        startActivityForResult(Intent.createChooser(selectPictureIntent,"Select Picture"),REQUEST_FOR_IMAGE);

    }


    private File createImageFile() throws IOException{
        //create an image file name
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String imageFileName = "JPEG" + timeStamp + "_";
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        File image = File.createTempFile(imageFileName,".jpg",storageDir);
        //Save a file: path for use with ACION_VIEW intents
        mCurrentPhotoPath = image.getAbsolutePath();
        return image;
    }

    @Override
    protected void onActivityResult(int requestCode,int resultCode,Intent data){
        if(requestCode == REQUEST_IMAGE_CAPTURE && resultCode == RESULT_OK)
        {
            //Scales and sets the image capture in dispatchTakePictureIntent() in imageView
            setPic();
            detectedText();
            setTextOnScreen();
        }
        if(requestCode == REQUEST_FOR_IMAGE && resultCode == RESULT_OK){
            //mCurrentPhotoPath = getPathFromUri(data.getData());
            try{
                InputStream inputStream = getApplicationContext().getContentResolver().openInputStream(data.getData());
                File photoFile = createImageFile();
                if(photoFile != null) {
                    Uri photoURI = FileProvider.getUriForFile(this,
                            "com.example.karanacer.textvision",
                            photoFile);
                    OutputStream outputStream = getContentResolver().openOutputStream(photoURI);
                    try {
                        // Transfer bytes from in to out
                        byte[] buf = new byte[1024];
                        int len;
                        while ((len = inputStream.read(buf)) > 0) {
                            outputStream.write(buf, 0, len);
                        }
                    }
                    catch(Exception e){

                    }
                    finally {
                        outputStream.close();
                        inputStream.close();
                    }
                }
            }
            catch(Exception e){

            }
            setPic();
            detectedText();
            setTextOnScreen();
        }
    }

    private void setPic(){
        mImageView = (ImageView) findViewById(R.id.imageView);
        //Get dimensions of the imageView in the app layout
        int targetW = mImageView.getWidth();
        int targetH = mImageView.getHeight();
        //Get dimensions of image captured in dispatchTakePictureIntent()
        BitmapFactory.Options bmOptions = new BitmapFactory.Options();
        bmOptions.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(mCurrentPhotoPath,bmOptions);
        int photoW = bmOptions.outWidth;
        int photoH = bmOptions.outHeight;
        //scale image to match imageView layout
        int scaleFactor = Math.min(photoH/targetH,photoW/targetW);

        bmOptions.inJustDecodeBounds = false;
        bmOptions.inSampleSize = scaleFactor;
        bmOptions.inPurgeable = true;
        //Set the decoded bitmap to imageView
        Bitmap mBitmap = BitmapFactory.decodeFile(mCurrentPhotoPath,bmOptions);
        mImageView.setImageBitmap(mBitmap);

    }

    private void detectedText(){
        TextRecognizer textRecognizer = new TextRecognizer.Builder(getApplicationContext()).build();
        try{
            Bitmap mBitmap = BitmapFactory.decodeFile(mCurrentPhotoPath);
            if(textRecognizer.isOperational() && mBitmap != null) {
                //Creates a frame to be sent to the Text Detector
                Frame currentImage = new Frame.Builder()
                        .setBitmap(mBitmap)
                        .build();
                //Text detected in image is stored in text blocks
                SparseArray<TextBlock> textBlocks = textRecognizer.detect(currentImage);
                if(textBlocks.size() != 0){
                    for (int i = 0; i < textBlocks.size(); i++) {
                        TextBlock temp = textBlocks.get(textBlocks.keyAt(i));
                        //Text is stored line wise
                        imageText += "\n" + temp.getValue();
                    }
                }
                else{
                    Toast.makeText(this,"No Text detected, works on printed black text with a white background",Toast.LENGTH_SHORT)
                            .show();
                }

            }
            else {
                Toast.makeText(this,"Could not set up the detector!",Toast.LENGTH_SHORT)
                        .show();
            }
        }
        catch(Exception e){
            Toast.makeText(this, "Failed to load Image", Toast.LENGTH_SHORT)
                    .show();
        }

    }

    private void setTextOnScreen(){
        mTextView = (TextView) findViewById(R.id.textView);
        //The detected text is displayed in the textview
        mTextView.setText(imageText);
        //Sets the save text button to be clickable
        mSaveButton = (Button) findViewById(R.id.saveToFile);
        mSaveButton.setEnabled(true);

    }

    private void saveToTextFile(){
        //create an text file name
        String textFileName = "imageText";
        File storageDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        try{
            String pathToFile = storageDir + "/" + textFileName +".txt";
            File textFile = new File(pathToFile);
            FileOutputStream fos = new FileOutputStream(textFile);
            fos.write(imageText.getBytes());    //Write detected text in the image to the file
            fos.close();
            Toast.makeText(this,"Text successfully saved in "+ pathToFile,Toast.LENGTH_SHORT);
            Toast.makeText(this,"File contents will be overwritten when new image is processed",Toast.LENGTH_SHORT);
        }
        catch(Exception e)
        {
            Toast.makeText(this, "Failed to create Text File", Toast.LENGTH_SHORT).show();
        }

    }
}
