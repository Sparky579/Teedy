package com.sismics.docs.rest.resource;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.text.MessageFormat;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.glassfish.jersey.media.multipart.FormDataBodyPart;
import org.glassfish.jersey.media.multipart.FormDataParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.io.ByteStreams;
import com.sismics.docs.core.constant.PermType;
import com.sismics.docs.core.dao.AclDao;
import com.sismics.docs.core.dao.DocumentDao;
import com.sismics.docs.core.dao.FileDao;
import com.sismics.docs.core.dao.TagDao;
import com.sismics.docs.core.dao.UserDao;
import com.sismics.docs.core.dao.criteria.TagCriteria;
import com.sismics.docs.core.dao.dto.DocumentDto;
import com.sismics.docs.core.dao.dto.TagDto;
import com.sismics.docs.core.event.DocumentUpdatedAsyncEvent;
import com.sismics.docs.core.event.FileDeletedAsyncEvent;
import com.sismics.docs.core.event.FileUpdatedAsyncEvent;
import com.sismics.docs.core.model.context.AppContext;
import com.sismics.docs.core.model.jpa.File;
import com.sismics.docs.core.model.jpa.Tag;
import com.sismics.docs.core.model.jpa.User;
import com.sismics.docs.core.util.DirectoryUtil;
import com.sismics.docs.core.util.EncryptionUtil;
import com.sismics.docs.core.util.FileUtil;
import com.sismics.docs.core.util.LLMUtil;
import com.sismics.docs.core.util.format.FormatHandler;
import com.sismics.docs.core.util.format.FormatHandlerUtil;
import com.sismics.rest.exception.ClientException;
import com.sismics.rest.exception.ForbiddenClientException;
import com.sismics.rest.exception.ServerException;
import com.sismics.rest.util.RestUtil;
import com.sismics.rest.util.ValidationUtil;
import com.sismics.util.HttpUtil;
import com.sismics.util.JsonUtil;
import com.sismics.util.context.ThreadLocalContext;
import com.sismics.util.mime.MimeType;
import com.sismics.util.mime.MimeTypeUtil;

import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObjectBuilder;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.core.StreamingOutput;

/**
 * File REST resources.
 * 
 * @author bgamard
 */
@Path("/file")
public class FileResource extends BaseResource {
    /**
     * Logger.
     */
    private static final Logger log = LoggerFactory.getLogger(FileResource.class);

    /**
     * Add a file (with or without a document).
     *
     * @api {put} /file Add a file
     * @apiDescription A file can be added without associated document, and will go in a temporary storage waiting for one.
     * This resource accepts only multipart/form-data.
     * @apiName PutFile
     * @apiGroup File
     * @apiParam {String} [id] Document ID
     * @apiParam {String} [previousFileId] ID of the file to replace by this new version
     * @apiParam {String} file File data
     * @apiSuccess {String} status Status OK
     * @apiSuccess {String} id File ID
     * @apiSuccess {Number} size File size (in bytes)
     * @apiError (client) ForbiddenError Access denied
     * @apiError (client) ValidationError Validation error
     * @apiError (client) NotFound Document not found
     * @apiError (server) StreamError Error reading the input file
     * @apiError (server) ErrorGuessMime Error guessing mime type
     * @apiError (client) QuotaReached Quota limit reached
     * @apiError (server) FileError Error adding a file
     * @apiPermission user
     * @apiVersion 1.5.0
     *
     * @param documentId Document ID
     * @param fileBodyPart File to add
     * @return Response
     */
    @PUT
    @Consumes("multipart/form-data")
    public Response add(
            @FormDataParam("id") String documentId,
            @FormDataParam("previousFileId") String previousFileId,
            @FormDataParam("file") FormDataBodyPart fileBodyPart) {
        if (!authenticate()) {
            throw new ForbiddenClientException();
        }
        
        // Validate input data
        ValidationUtil.validateRequired(fileBodyPart, "file");

        // Get the document
        DocumentDto documentDto = null;
        if (Strings.isNullOrEmpty(documentId)) {
            documentId = null;
        } else {
            DocumentDao documentDao = new DocumentDao();
            documentDto = documentDao.getDocument(documentId, PermType.WRITE, getTargetIdList(null));
            if (documentDto == null) {
                throw new NotFoundException();
            }
        }
        
        // Keep unencrypted data temporary on disk
        String name = fileBodyPart.getContentDisposition() != null ?
                URLDecoder.decode(fileBodyPart.getContentDisposition().getFileName(), StandardCharsets.UTF_8) : null;
        java.nio.file.Path unencryptedFile;
        long fileSize;
        try {
            unencryptedFile = AppContext.getInstance().getFileService().createTemporaryFile(name);
            Files.copy(fileBodyPart.getValueAs(InputStream.class), unencryptedFile, StandardCopyOption.REPLACE_EXISTING);
            fileSize = Files.size(unencryptedFile);
        } catch (IOException e) {
            throw new ServerException("StreamError", "Error reading the input file", e);
        }

        try {
            String fileId = FileUtil.createFile(name, previousFileId, unencryptedFile, fileSize, documentDto == null ?
                    null : documentDto.getLanguage(), principal.getId(), documentId);
            
            // 如果有关联的文档，则自动添加标签
            if (documentId != null) {
                // 获取文件MIME类型
                String mimeType = MimeTypeUtil.guessMimeType(unencryptedFile, name);
                
                // 自动添加标签逻辑
                autoAddTagByMimeType(documentId, mimeType);
            }

            // Always return OK
            JsonObjectBuilder response = Json.createObjectBuilder()
                    .add("status", "ok")
                    .add("id", fileId)
                    .add("size", fileSize);
            return Response.ok().entity(response.build()).build();
        } catch (IOException e) {
            throw new ClientException(e.getMessage(), e.getMessage(), e);
        } catch (Exception e) {
            throw new ServerException("FileError", "Error adding a file", e);
        }
    }
    
    /**
     * 根据MIME类型自动添加标签
     * 
     * @param documentId 文档ID
     * @param mimeType MIME类型
     */
    private void autoAddTagByMimeType(String documentId, String mimeType) {
        if (mimeType == null) return;
        
        try {
            // 查找现有的标签
            TagDao tagDao = new TagDao();
            List<TagDto> allTags = tagDao.findByCriteria(new TagCriteria(), null);
            List<TagDto> documentTags = tagDao.findByCriteria(new TagCriteria().setDocumentId(documentId), null);
            Set<String> tagIdSet = new HashSet<>();
            
            // 添加文档现有的标签ID到集合中
            for (TagDto documentTag : documentTags) {
                tagIdSet.add(documentTag.getId());
            }
            
            // 先添加基本的MIME类型标签
            String basicTagName = null;
            if (mimeType.startsWith("image/")) {
                basicTagName = "image";
            } else if (mimeType.startsWith("text/") || 
                       mimeType.equals("application/pdf") || 
                       mimeType.equals("application/msword") || 
                       mimeType.contains("document") ||
                       mimeType.contains("pdf") ||
                       mimeType.contains("text")) {
                basicTagName = "text";
                
                // 仅对文本类型的文件进行内容分析和智能标签生成
                String content = null;
                
                // 获取文档内容进行分析
                try {
                    // 获取与文档关联的文件
                    FileDao fileDao = new FileDao();
                    List<File> files = fileDao.getByDocumentId(principal.getId(), documentId);
                    if (!files.isEmpty()) {
                        // 对于text类型文件，尝试提取内容
                        File file = files.get(0); // 使用第一个文件
                        UserDao userDao = new UserDao();
                        User user = userDao.getById(file.getUserId());

                        // 获取解密的文件
                        java.nio.file.Path storedFile = DirectoryUtil.getStorageDirectory().resolve(file.getId());
                        java.nio.file.Path unencryptedFile = EncryptionUtil.decryptFile(storedFile, user.getPrivateKey());
                        
                        // 查找适合此MIME类型的格式处理器
                        FormatHandler formatHandler = FormatHandlerUtil.find(file.getMimeType());
                        if (formatHandler != null) {
                            // 提取内容
                            DocumentDao documentDao = new DocumentDao();
                            DocumentDto documentDto = documentDao.getDocument(documentId, PermType.READ, getTargetIdList(null));
                            if (documentDto != null) {
                                content = formatHandler.extractContent(documentDto.getLanguage(), unencryptedFile);
                            }
                        }
                    }
                } catch (Exception e) {
                    log.error("Error extracting content for LLM analysis", e);
                }
                
                // 如果成功提取到内容，使用LLM分析生成标签
                if (content != null && !content.trim().isEmpty()) {
                    List<String> suggestedTags = LLMUtil.suggestTags(content, 3000);
                    
                    // 为每个建议的标签创建或查找对应的标签ID
                    for (String tagName : suggestedTags) {
                        // 检查这个标签是否已存在
                        String tagId = null;
                        for (TagDto tagDto : allTags) {
                            if (tagName.equalsIgnoreCase(tagDto.getName())) {
                                tagId = tagDto.getId();
                                break;
                            }
                        }
                        
                        // 如果标签不存在，则创建
                        if (tagId == null) {
                            Tag tag = new Tag();
                            tag.setName(tagName);
                            tag.setColor("#3a87ad"); // 默认蓝色
                            tag.setUserId(principal.getId());
                            tagId = tagDao.create(tag, principal.getId());
                            
                            // 添加到allTags列表，以避免重复创建
                            TagDto newTagDto = new TagDto();
                            newTagDto.setId(tagId);
                            newTagDto.setName(tagName);
                            allTags.add(newTagDto);
                        }
                        
                        // 添加标签ID到集合
                        tagIdSet.add(tagId);
                    }
                }
            }
            
            // 添加基本MIME类型标签
            if (basicTagName != null) {
                // 检查基本标签是否已存在
                String tagId = null;
                for (TagDto tagDto : allTags) {
                    if (basicTagName.equals(tagDto.getName())) {
                        tagId = tagDto.getId();
                        break;
                    }
                }
                
                // 如果标签不存在，则创建
                if (tagId == null) {
                    Tag tag = new Tag();
                    tag.setName(basicTagName);
                    tag.setColor("#3a87ad"); // 默认蓝色
                    tag.setUserId(principal.getId());
                    tagId = tagDao.create(tag, principal.getId());
                }
                
                // 添加标签ID到集合
                tagIdSet.add(tagId);
            }
            
            // 更新文档的标签列表
            if (!tagIdSet.isEmpty()) {
                tagDao.updateTagList(documentId, tagIdSet);
            }
        } catch (Exception e) {
            // 记录错误但不中断流程
            // 因为添加标签失败不应该导致文件上传失败
            log.error("Error adding tags by MIME type and content analysis", e);
        }
    }
    
    /**
     * Attach a file to a document.
     *
     * @api {post} /file/:fileId/attach Attach a file to a document
     * @apiName PostFileAttach
     * @apiGroup File
     * @apiParam {String} fileId File ID
     * @apiParam {String} id Document ID
     * @apiSuccess {String} status Status OK
     * @apiError (client) ForbiddenError Access denied
     * @apiError (client) ValidationError Validation error
     * @apiError (client) IllegalFile File not orphan
     * @apiError (server) AttachError Error attaching file to document
     * @apiPermission user
     * @apiVersion 1.5.0
     *
     * @param id File ID
     * @return Response
     */
    @POST
    @Path("{id: [a-z0-9\\-]+}/attach")
    public Response attach(
            @PathParam("id") String id,
            @FormParam("id") String documentId) {
        if (!authenticate()) {
            throw new ForbiddenClientException();
        }

        // Validate input data
        ValidationUtil.validateRequired(documentId, "documentId");
        
        // Get the current user
        UserDao userDao = new UserDao();
        User user = userDao.getById(principal.getId());
        
        // Get the document and the file
        DocumentDao documentDao = new DocumentDao();
        FileDao fileDao = new FileDao();
        File file = fileDao.getFile(id, principal.getId());
        DocumentDto documentDto = documentDao.getDocument(documentId, PermType.WRITE, getTargetIdList(null));
        if (file == null || documentDto == null) {
            throw new NotFoundException();
        }
        
        // Check that the file is orphan
        if (file.getDocumentId() != null) {
            throw new ClientException("IllegalFile", MessageFormat.format("File not orphan: {0}", id));
        }
        
        // Update the file
        file.setDocumentId(documentId);
        file.setOrder(fileDao.getByDocumentId(principal.getId(), documentId).size());
        fileDao.update(file);
        
        // 获取文件MIME类型并添加标签
        try {
            autoAddTagByMimeType(documentId, file.getMimeType());
        } catch (Exception e) {
            // 添加标签发生错误不影响正常操作
            log.error("Error adding tag when attaching file", e);
        }
        
        // Raise a new file updated event and document updated event (it wasn't sent during file creation)
        try {
            java.nio.file.Path storedFile = DirectoryUtil.getStorageDirectory().resolve(id);
            java.nio.file.Path unencryptedFile = EncryptionUtil.decryptFile(storedFile, user.getPrivateKey());
            FileUtil.startProcessingFile(id);
            FileUpdatedAsyncEvent fileUpdatedAsyncEvent = new FileUpdatedAsyncEvent();
            fileUpdatedAsyncEvent.setUserId(principal.getId());
            fileUpdatedAsyncEvent.setLanguage(documentDto.getLanguage());
            fileUpdatedAsyncEvent.setFileId(file.getId());
            fileUpdatedAsyncEvent.setUnencryptedFile(unencryptedFile);
            ThreadLocalContext.get().addAsyncEvent(fileUpdatedAsyncEvent);
            
            DocumentUpdatedAsyncEvent documentUpdatedAsyncEvent = new DocumentUpdatedAsyncEvent();
            documentUpdatedAsyncEvent.setUserId(principal.getId());
            documentUpdatedAsyncEvent.setDocumentId(documentId);
            ThreadLocalContext.get().addAsyncEvent(documentUpdatedAsyncEvent);
        } catch (Exception e) {
            throw new ServerException("AttachError", "Error attaching file to document", e);
        }

        // Always return OK
        JsonObjectBuilder response = Json.createObjectBuilder()
                .add("status", "ok");
        return Response.ok().entity(response.build()).build();
    }

    /**
     * Update a file.
     *
     * @api {post} /file/:id Update a file
     * @apiName PostFile
     * @apiGroup File
     * @apiParam {String} id File ID
     * @apiParam {String} name Name
     * @apiSuccess {String} status Status OK
     * @apiError (client) ForbiddenError Access denied
     * @apiError (client) ValidationError Validation error
     * @apiPermission user
     * @apiVersion 1.6.0
     *
     * @param id File ID
     * @return Response
     */
    @POST
    @Path("{id: [a-z0-9\\-]+}")
    public Response update(@PathParam("id") String id,
                           @FormParam("name") String name) {
        if (!authenticate()) {
            throw new ForbiddenClientException();
        }

        // Get the file
        File file = findFile(id, null);

        // Validate input data
        name = ValidationUtil.validateLength(name, "name", 1, 200, false);

        // Update the file
        FileDao fileDao = new FileDao();
        file.setName(name);
        fileDao.update(file);

        // Always return OK
        JsonObjectBuilder response = Json.createObjectBuilder()
                .add("status", "ok");
        return Response.ok().entity(response.build()).build();
    }

    /**
     * Process a file manually.
     *
     * @api {post} /file/:id/process Process a file manually
     * @apiName PostFileProcess
     * @apiGroup File
     * @apiParam {String} id File ID
     * @apiSuccess {String} status Status OK
     * @apiError (client) ForbiddenError Access denied
     * @apiError (client) ValidationError Validation error
     * @apiError (server) ProcessingError Processing error
     * @apiPermission user
     * @apiVersion 1.6.0
     *
     * @param id File ID
     * @return Response
     */
    @POST
    @Path("{id: [a-z0-9\\-]+}/process")
    public Response process(@PathParam("id") String id) {
        if (!authenticate()) {
            throw new ForbiddenClientException();
        }

        // Get the document and the file
        DocumentDao documentDao = new DocumentDao();
        FileDao fileDao = new FileDao();
        File file = fileDao.getFile(id);
        if (file == null || file.getDocumentId() == null) {
            throw new NotFoundException();
        }
        DocumentDto documentDto = documentDao.getDocument(file.getDocumentId(), PermType.WRITE, getTargetIdList(null));
        if (documentDto == null) {
            throw new NotFoundException();
        }

        // Get the creating user
        UserDao userDao = new UserDao();
        User user = userDao.getById(file.getUserId());

        // Start the processing asynchronously
        try {
            java.nio.file.Path storedFile = DirectoryUtil.getStorageDirectory().resolve(id);
            java.nio.file.Path unencryptedFile = EncryptionUtil.decryptFile(storedFile, user.getPrivateKey());
            FileUtil.startProcessingFile(id);
            FileUpdatedAsyncEvent event = new FileUpdatedAsyncEvent();
            event.setUserId(principal.getId());
            event.setLanguage(documentDto.getLanguage());
            event.setFileId(file.getId());
            event.setUnencryptedFile(unencryptedFile);
            ThreadLocalContext.get().addAsyncEvent(event);
        } catch (Exception e) {
            throw new ServerException("ProcessingError", "Error processing this file", e);
        }

        // Always return OK
        JsonObjectBuilder response = Json.createObjectBuilder()
                .add("status", "ok");
        return Response.ok().entity(response.build()).build();
    }
    
    /**
     * Reorder files.
     *
     * @api {post} /file/:reorder Reorder files
     * @apiName PostFileReorder
     * @apiGroup File
     * @apiParam {String} id Document ID
     * @apiParam {String[]} order List of files ID
     * @apiSuccess {String} status Status OK
     * @apiError (client) ForbiddenError Access denied
     * @apiError (client) ValidationError Validation error
     * @apiError (client) NotFound Document not found
     * @apiPermission user
     * @apiVersion 1.5.0
     *
     * @param documentId Document ID
     * @param idList List of files ID in the new order
     * @return Response
     */
    @POST
    @Path("reorder")
    public Response reorder(
            @FormParam("id") String documentId,
            @FormParam("order") List<String> idList) {
        if (!authenticate()) {
            throw new ForbiddenClientException();
        }
        
        // Validate input data
        ValidationUtil.validateRequired(documentId, "id");
        ValidationUtil.validateRequired(idList, "order");
        
        // Get the document
        AclDao aclDao = new AclDao();
        if (!aclDao.checkPermission(documentId, PermType.WRITE, getTargetIdList(null))) {
            throw new NotFoundException();
        }
        
        // Reorder files
        FileDao fileDao = new FileDao();
        for (File file : fileDao.getByDocumentId(principal.getId(), documentId)) {
            int order = idList.lastIndexOf(file.getId());
            if (order != -1) {
                file.setOrder(order);
            }
        }

        // Raise a document updated event
        DocumentUpdatedAsyncEvent event = new DocumentUpdatedAsyncEvent();
        event.setUserId(principal.getId());
        event.setDocumentId(documentId);
        ThreadLocalContext.get().addAsyncEvent(event);
        
        // Always return OK
        JsonObjectBuilder response = Json.createObjectBuilder()
                .add("status", "ok");
        return Response.ok().entity(response.build()).build();
    }
    
    /**
     * Returns files linked to a document or not linked to any document.
     *
     * @api {get} /file/list Get files
     * @apiName GetFileList
     * @apiGroup File
     * @apiParam {String} [id] Document ID
     * @apiParam {String} [share] Share ID
     * @apiSuccess {Object[]} files List of files
     * @apiSuccess {String} files.id ID
     * @apiSuccess {String} files.processing True if the file is currently processing
     * @apiSuccess {String} files.name File name
     * @apiSuccess {String} files.version Zero-based version number
     * @apiSuccess {String} files.mimetype MIME type
     * @apiSuccess {String} files.document_id Document ID
     * @apiSuccess {String} files.create_date Create date (timestamp)
     * @apiSuccess {String} files.size File size (in bytes)
     * @apiError (client) ForbiddenError Access denied
     * @apiError (client) NotFound Document not found
     * @apiError (server) FileError Unable to get the size of a file
     * @apiPermission none
     * @apiVersion 1.5.0
     *
     * @param documentId Document ID
     * @param shareId Sharing ID
     * @return Response
     */
    @GET
    @Path("list")
    public Response list(
            @QueryParam("id") String documentId,
            @QueryParam("share") String shareId) {
        boolean authenticated = authenticate();
        
        // Check document visibility
        if (documentId != null) {
            AclDao aclDao = new AclDao();
            if (!aclDao.checkPermission(documentId, PermType.READ, getTargetIdList(shareId))) {
                throw new NotFoundException();
            }
        } else if (!authenticated) {
            throw new ForbiddenClientException();
        }

        FileDao fileDao = new FileDao();
        JsonArrayBuilder files = Json.createArrayBuilder();
        for (File fileDb : fileDao.getByDocumentId(principal.getId(), documentId)) {
            files.add(RestUtil.fileToJsonObjectBuilder(fileDb));
        }
        JsonObjectBuilder response = Json.createObjectBuilder()
                .add("files", files);

        return Response.ok().entity(response.build()).build();
    }

    /**
     * List all versions of a file.
     *
     * @api {get} /file/:id/versions Get versions of a file
     * @apiName GetFileVersions
     * @apiGroup File
     * @apiParam {String} id File ID
     * @apiSuccess {Object[]} files List of files
     * @apiSuccess {String} files.id ID
     * @apiSuccess {String} files.name File name
     * @apiSuccess {String} files.version Zero-based version number
     * @apiSuccess {String} files.mimetype MIME type
     * @apiSuccess {String} files.create_date Create date (timestamp)
     * @apiError (client) ForbiddenError Access denied
     * @apiError (client) NotFound File not found
     * @apiPermission user
     * @apiVersion 1.5.0
     *
     * @param id File ID
     * @return Response
     */
    @GET
    @Path("{id: [a-z0-9\\-]+}/versions")
    public Response versions(@PathParam("id") String id) {
        if (!authenticate()) {
            throw new ForbiddenClientException();
        }

        // Get versions
        File file = findFile(id, null);
        FileDao fileDao = new FileDao();
        List<File> fileList = Lists.newArrayList(file);
        if (file.getVersionId() != null) {
            fileList = fileDao.getByVersionId(file.getVersionId());
        }

        JsonArrayBuilder files = Json.createArrayBuilder();
        for (File fileDb : fileList) {
            files.add(Json.createObjectBuilder()
                    .add("id", fileDb.getId())
                    .add("name", JsonUtil.nullable(fileDb.getName()))
                    .add("version", fileDb.getVersion())
                    .add("mimetype", fileDb.getMimeType())
                    .add("create_date", fileDb.getCreateDate().getTime()));
        }

        JsonObjectBuilder response = Json.createObjectBuilder()
                .add("files", files);
        return Response.ok().entity(response.build()).build();
    }
    
    /**
     * Deletes a file.
     *
     * @api {delete} /file/:id Delete a file
     * @apiName DeleteFile
     * @apiGroup File
     * @apiParam {String} id File ID
     * @apiSuccess {String} status Status OK
     * @apiError (client) ForbiddenError Access denied
     * @apiError (client) NotFound File or document not found
     * @apiPermission user
     * @apiVersion 1.5.0
     *
     * @param id File ID
     * @return Response
     */
    @DELETE
    @Path("{id: [a-z0-9\\-]+}")
    public Response delete(
            @PathParam("id") String id) {
        if (!authenticate()) {
            throw new ForbiddenClientException();
        }

        // Get the file
        File file = findFile(id, null);

        // Delete the file
        FileDao fileDao = new FileDao();
        fileDao.delete(file.getId(), principal.getId());
        
        // Raise a new file deleted event
        FileDeletedAsyncEvent fileDeletedAsyncEvent = new FileDeletedAsyncEvent();
        fileDeletedAsyncEvent.setUserId(principal.getId());
        fileDeletedAsyncEvent.setFileId(file.getId());
        fileDeletedAsyncEvent.setFileSize(file.getSize());
        ThreadLocalContext.get().addAsyncEvent(fileDeletedAsyncEvent);
        
        if (file.getDocumentId() != null) {
            // Raise a new document updated
            DocumentUpdatedAsyncEvent documentUpdatedAsyncEvent = new DocumentUpdatedAsyncEvent();
            documentUpdatedAsyncEvent.setUserId(principal.getId());
            documentUpdatedAsyncEvent.setDocumentId(file.getDocumentId());
            ThreadLocalContext.get().addAsyncEvent(documentUpdatedAsyncEvent);
        }
        
        // Always return OK
        JsonObjectBuilder response = Json.createObjectBuilder()
                .add("status", "ok");
        return Response.ok().entity(response.build()).build();
    }
    
    /**
     * Returns a file.
     *
     * @api {get} /file/:id/data Get a file data
     * @apiName GetFile
     * @apiGroup File
     * @apiParam {String} id File ID
     * @apiParam {String} share Share ID
     * @apiParam {String="web","thumb","content"} [size] Size variation
     * @apiSuccess {Object} file The file data is the whole response
     * @apiError (client) SizeError Size must be web or thumb
     * @apiError (client) ForbiddenError Access denied or document not visible
     * @apiError (client) NotFound File not found
     * @apiError (server) ServiceUnavailable Error reading the file
     * @apiPermission none
     * @apiVersion 1.5.0
     *
     * @param fileId File ID
     * @return Response
     */
    @GET
    @Path("{id: [a-z0-9\\-]+}/data")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public Response data(
            @PathParam("id") final String fileId,
            @QueryParam("share") String shareId,
            @QueryParam("size") String size) {
        authenticate();
        
        if (size != null && !Lists.newArrayList("web", "thumb", "content").contains(size)) {
            throw new ClientException("SizeError", "Size must be web, thumb or content");
        }

        // Get the file
        File file = findFile(fileId, shareId);

        // Get the stored file
        UserDao userDao = new UserDao();
        java.nio.file.Path storedFile;
        String mimeType;
        boolean decrypt;
        if (size != null) {
            if (size.equals("content")) {
                return Response.ok(Strings.nullToEmpty(file.getContent()))
                        .header(HttpHeaders.CONTENT_TYPE, "text/plain; charset=utf-8")
                        .build();
            }

            storedFile = DirectoryUtil.getStorageDirectory().resolve(fileId + "_" + size);
            mimeType = MimeType.IMAGE_JPEG; // Thumbnails are JPEG
            decrypt = true; // Thumbnails are encrypted
            if (!Files.exists(storedFile)) {
                try {
                    storedFile = Paths.get(getClass().getResource("/image/file-" + size + ".png").toURI());
                } catch (URISyntaxException e) {
                    // Ignore
                }
                mimeType = MimeType.IMAGE_PNG;
                decrypt = false;
            }
        } else {
            storedFile = DirectoryUtil.getStorageDirectory().resolve(fileId);
            mimeType = file.getMimeType();
            decrypt = true; // Original files are encrypted
        }
        
        // Stream the output and decrypt it if necessary
        StreamingOutput stream;
        
        // A file is always encrypted by the creator of it
        User user = userDao.getById(file.getUserId());
        
        // Write the decrypted file to the output
        try {
            InputStream fileInputStream = Files.newInputStream(storedFile);
            final InputStream responseInputStream = decrypt ?
                    EncryptionUtil.decryptInputStream(fileInputStream, user.getPrivateKey()) : fileInputStream;
                    
            stream = outputStream -> {
                try {
                    ByteStreams.copy(responseInputStream, outputStream);
                } finally {
                    try {
                        responseInputStream.close();
                        outputStream.close();
                    } catch (IOException e) {
                        // Ignore
                    }
                }
            };
        } catch (Exception e) {
            return Response.status(Status.SERVICE_UNAVAILABLE).build();
        }

        Response.ResponseBuilder builder = Response.ok(stream)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + file.getFullName("data") + "\"")
                .header(HttpHeaders.CONTENT_TYPE, mimeType);
        if (decrypt) {
            // Cache real files
            builder.header(HttpHeaders.CACHE_CONTROL, "private")
                    .header(HttpHeaders.EXPIRES, HttpUtil.buildExpiresHeader(3_600_000L * 24L * 365L));
        } else {
            // Do not cache the temporary thumbnail
            builder.header(HttpHeaders.CACHE_CONTROL, "no-store, must-revalidate")
                    .header(HttpHeaders.EXPIRES, "0");
        }
        return builder.build();
    }

    /**
     * Returns all files from a document, zipped.
     *
     * @api {get} /file/zip Returns all files from a document, zipped.
     * @apiName GetFileZip
     * @apiGroup File
     * @apiParam {String} id Document ID
     * @apiParam {String} share Share ID
     * @apiSuccess {Object} file The ZIP file is the whole response
     * @apiError (client) NotFoundException Document not found
     * @apiError (server) InternalServerError Error creating the ZIP file
     * @apiPermission none
     * @apiVersion 1.5.0
     *
     * @param documentId Document ID
     * @param shareId Share ID
     * @return Response
     */
    @GET
    @Path("zip")
    @Produces({MediaType.APPLICATION_OCTET_STREAM, MediaType.TEXT_PLAIN})
    public Response zip(
            @QueryParam("id") String documentId,
            @QueryParam("share") String shareId) {
        authenticate();
        
        // Get the document
        DocumentDao documentDao = new DocumentDao();
        DocumentDto documentDto = documentDao.getDocument(documentId, PermType.READ, getTargetIdList(shareId));
        if (documentDto == null) {
            throw new NotFoundException();
        }

        // Get files associated with this document
        FileDao fileDao = new FileDao();
        final List<File> fileList = fileDao.getByDocumentId(principal.getId(), documentId);
        String zipFileName = documentDto.getTitle().replaceAll("\\W+", "_");
        return sendZippedFiles(zipFileName, fileList);
    }

    /**
     * Returns a list of files, zipped
     *
     * @api {post} /file/zip Returns a list of files, zipped
     * @apiName GetFilesZip
     * @apiGroup File
     * @apiParam {String[]} files IDs
     * @apiSuccess {Object} file The ZIP file is the whole response
     * @apiError (client) NotFoundException Files not found
     * @apiError (server) InternalServerError Error creating the ZIP file
     * @apiPermission none
     * @apiVersion 1.11.0
     *
     * @param filesIdsList Files IDs
     * @return Response
     */
    @POST
    @Path("zip")
    @Produces({MediaType.APPLICATION_OCTET_STREAM, MediaType.TEXT_PLAIN})
    public Response zip(
            @FormParam("files") List<String> filesIdsList) {
        authenticate();
        List<File> fileList = findFiles(filesIdsList);
        return sendZippedFiles("files", fileList);
    }

    /**
     * Sent the content of a list of files.
     */
    private Response sendZippedFiles(String zipFileName, List<File> fileList) {
        final UserDao userDao = new UserDao();

        // Create the ZIP stream
        StreamingOutput stream = outputStream -> {
            try (ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream)) {
                // Add each file to the ZIP stream
                int index = 0;
                for (File file : fileList) {
                    java.nio.file.Path storedfile = DirectoryUtil.getStorageDirectory().resolve(file.getId());
                    InputStream fileInputStream = Files.newInputStream(storedfile);

                    // Add the decrypted file to the ZIP stream
                    // Files are encrypted by the creator of them
                    User user = userDao.getById(file.getUserId());
                    try (InputStream decryptedStream = EncryptionUtil.decryptInputStream(fileInputStream, user.getPrivateKey())) {
                        ZipEntry zipEntry = new ZipEntry(index + "-" + file.getFullName(Integer.toString(index)));
                        zipOutputStream.putNextEntry(zipEntry);
                        ByteStreams.copy(decryptedStream, zipOutputStream);
                        zipOutputStream.closeEntry();
                    } catch (Exception e) {
                        throw new WebApplicationException(e);
                    }
                    index++;
                }
            }
            outputStream.close();
        };
        
        // Write to the output
        return Response.ok(stream)
                .header("Content-Type", "application/zip")
                .header("Content-Disposition", "attachment; filename=\"" + zipFileName + ".zip\"")
                .build();
    }

    /**
     * Find a file with access rights checking.
     *
     * @param fileId File ID
     * @param shareId Share ID
     * @return File
     */
    private File findFile(String fileId, String shareId) {
        FileDao fileDao = new FileDao();
        File file = fileDao.getFile(fileId);
        if (file == null) {
            throw new NotFoundException();
        }
        checkFileAccessible(shareId, file);
        return file;
    }


    /**
     * Find a list of files with access rights checking.
     *
     * @param filesIds Files IDs
     * @return List<File>
     */
    private List<File> findFiles(List<String> filesIds) {
        FileDao fileDao = new FileDao();
        List<File> files = fileDao.getFiles(filesIds);
        for (File file : files) {
            checkFileAccessible(null, file);
        }
        return files;
    }

    /**
     * Check if a file is accessible to the current user
     * @param shareId Share ID
     * @param file
     */
    private void checkFileAccessible(String shareId, File file) {
        if (file.getDocumentId() == null) {
            // It's an orphan file
            if (!file.getUserId().equals(principal.getId())) {
                // But not ours
                throw new ForbiddenClientException();
            }
        } else {
            // Check document accessibility
            AclDao aclDao = new AclDao();
            if (!aclDao.checkPermission(file.getDocumentId(), PermType.READ, getTargetIdList(shareId))) {
                throw new ForbiddenClientException();
            }
        }
    }
}
