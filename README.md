LockPic-server
==============

This project holds the companion server for the LockPic application, available here: https://github.com/nicslabdev/LockPic

The server acts as a key repository for the application, which will query it when trying to encrypt or decrypt an image.

It has been developed to run on Google App Engine; however, its adaptation to any other platform would be straightforward; just changing the authentication procedure, and the handling of the database.

The server's interface with the application is through HTTP messages (over HTTPS), backed by a JSON API.

The server's response to any query will always be a JSON array, where the first element will contain generic information regarding the request, and if necessary, other elements will contain further data.
This first element (to which we will refer as header from here on) will always contain two parameters: protocolVersion and result. protocolVersion, as of now, is set to 1.0 (but could be used for future modifications), and result is either "ok" or "error". If it is error, two additional fields are required: reason and isAuthError. "reason" holds a brief explanation of the error, and isAuthError will be true if the authentication attempt failed (meaning the client's OAuth token should be refreshed), and false otherwise. Further fields can be included at will.

1. A GET message with query string action=requestInfo&requestedId=\<id\> asks the server for permission to decrypt the picture with identifier \<id\>.
The server will respond with a JSON array, with each element after the header containing the data for a region to be decrypted. These are sent with 5 fields: key (in Base64), and the four coordinates of the region as horizStart, horizEnd, vertStart, vertEnd respectively (in pixels and divided by 16; i.e. a region with its lower limit in x = 48 will have horizStart as 3.)

2. A POST message with query string action=oneStepUpload will initiate the encryption procedure. The body of the message will be a JSON array; its first element containing the protocolVersion field, as well as some metadata about the image - nothing but filename, width and height (in px). Each of the following elements contain the information of a region to be encrypted: as above, the four horizStart, horizEnd, vertStart, vertEnd fields (in pixels, divided by 16), and then the field username, which will contain a list of e-mail addresses which will be given permission for decrypting it.
The server will reply with a JSON array, with the usual header. In the second element, the database identifier for the image will be sent (in the field pictureId), and the following elements will each contain the key for a region (in the same order the client sent them), in a field named key.

3. A GET message with query string action=viewMyPictures will request a list of all pictures uploaded by the client. These will be returned as a JSON array, with the usual header, followed by a series of elements, each of which will contain the original filename (field filename), date of encryption (field date), and its identifier (pictureId).

4. A GET message with query string action=pictureDetails&requestedId=\<id\> will request a list of all permissions on a picture. These will be returned in a JSON array, with the first element containing the usual metadata, plus the image identifier (field pictureId) and its dimensions (width and height). Each of the following elements contains information on a permission line in the database; that is: the coordinates of the region (horizStart, horizEnd, yStart, yEnd), to whom it applies (username), and the identifier of the line in the database (id).

5. A POST message with query string action=update will notify the server to update some permissions. This requires the client to send a JSON array, where the first element will contain the usual metadata for queries (only protocolVersion, currently), plus the pictureId. Each of the following elements will contain a field named action, which will have the value "add" or "delete". If it is "delete", it must contain another field id for the identificator of the permission line. If it is "add", the information on the permission (horizStart, horizEnd, vertStart, vertEnd, and username, as described above) must be provided.



This project is protected by GNU General Public License v3.0.

Contact me at carlosparespulido (at) gmail (dot) com.
