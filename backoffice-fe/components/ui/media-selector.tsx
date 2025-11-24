"use client";

import React, { useState, useEffect } from "react";
import { Card, CardContent } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Badge } from "@/components/ui/badge";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import {
  Upload,
  Image,
  Trash2,
  Eye,
  Search,
  Check,
  Link,
  X,
} from "lucide-react";
import { toast } from "sonner";
import { mediaService } from "@/services/media-service";
import type { MediaResponse } from "@/types/api";

export interface SimpleMediaItem {
  id?: number; // Optional ID for database records
  mediaId?: number; // Media ID for database records
  publicId: string;
  url: string;
  secureUrl: string;
  format: string;
  width?: number;
  height?: number;
  bytes: number;
  folder?: string;
}

interface MediaSelectorProps {
  value?: string[] | number[] | MediaResponse[]; // Array of publicIds (string), mediaIds (number), or MediaResponse objects
  onChange?: (selectedMedia: string[]) => void; // Always returns publicIds for consistency
  onMediaChange?: (selectedMedia: MediaResponse[]) => void; // Returns complete MediaResponse objects
  onPrimaryChange?: (primaryImage: string | null) => void; // Primary image callback
  primaryImage?: string | null; // Current primary image publicId
  folder?:
    | "hotels"
    | "rooms"
    | "amenities"
    | "room-types"
    | "airlines"
    | "airports"
    | "flights"
    | "general"; // Specific folders we support
  maxSelection?: number;
  allowUpload?: boolean;
  className?: string;
  mode?: "publicIds" | "mediaIds"; // Determines how to interpret the value prop
  allowUrlInput?: boolean;
}

export function MediaSelector({
  value = [],
  onChange,
  onMediaChange,
  onPrimaryChange,
  primaryImage = null,
  folder = "general",
  maxSelection = 5,
  allowUpload = true,
  allowUrlInput = true,
  className = "",
  mode = "publicIds",
}: MediaSelectorProps) {
  // For hotel-related entities, always use 'hotels' folder
  const effectiveFolder = ["rooms", "amenities", "room-types"].includes(folder)
    ? "hotels"
    : folder;
  const [isOpen, setIsOpen] = useState(false);
  const [mediaItems, setMediaItems] = useState<SimpleMediaItem[]>([]);
  const [selectedMedia, setSelectedMedia] = useState<string[]>([]);
  const [selectedMediaObjects, setSelectedMediaObjects] = useState<
    MediaResponse[]
  >([]); // Store complete MediaResponse objects
  const [loading, setLoading] = useState(false);
  const [searchQuery, setSearchQuery] = useState("");
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [bulkFiles, setBulkFiles] = useState<File[]>([]);
  const [urlInput, setUrlInput] = useState<string>("");
  const [uploading, setUploading] = useState(false);
  const [isUploadingUrl, setIsUploadingUrl] = useState(false);
  const [currentPage, setCurrentPage] = useState(1);
  const [currentCursor, setCurrentCursor] = useState<string | null>(null);
  const [totalPages, setTotalPages] = useState(0); // For cursor-based pagination, 0 indicates unknown total
  const [hasNextPage, setHasNextPage] = useState(false);
  const [hasPreviousPage, setHasPreviousPage] = useState(false);
  const pageSize = 12;
  const [pageCursors, setPageCursors] = useState<{
    [key: number]: string | null;
  }>({}); // Track cursor for each page

  const fetchMediaItems = async () => {
    try {
      setLoading(true);

      // Determine which cursor to use for the request
      let requestCursor: string | null = null;
      if (currentPage > 1) {
        // For page > 1, use the cursor stored for this navigation step
        requestCursor = pageCursors[currentPage - 1] || null;
      } else {
        // For first page, use the current cursor (or null)
        requestCursor = currentCursor;
      }

      const result = await mediaService.searchMedia({
        folder: effectiveFolder || undefined,
        search: searchQuery || undefined,
        page: currentPage, // Still pass page for consistency
        limit: pageSize,
        nextCursor: requestCursor, // Use the correct cursor for this request
      });

      // Process the result to ensure correct format for SimpleMediaItem
      const processedItems = (result.items || []).map((item) => ({
        id: item.id,
        mediaId: item.mediaId,
        publicId: item.publicId,
        url: item.url,
        secureUrl: item.secureUrl,
        format: item.format,
        width: item.width,
        height: item.height,
        bytes: item.bytes,
        folder: item.folder,
      }));

      setMediaItems(processedItems);
      setTotalPages(result.totalPages || 1);
      setHasNextPage(result.hasNextPage || false);
      setHasPreviousPage(result.hasPreviousPage || false);

      // Update cursor for the next page navigation and track for navigation
      if (result.nextCursor) {
        setPageCursors((prev) => ({
          ...prev,
          [currentPage]: result.nextCursor || null,
        }));
        // Also update currentCursor for first page scenarios
        if (currentPage === 1) {
          setCurrentCursor(result.nextCursor);
        }
      }
    } catch (error) {
      console.error("Error fetching media:", error);
      // Show demo data for development - only hotel and flight related
      const hotelAndFlightFolders = [
        "hotels",
        "rooms",
        "amenities",
        "room-types",
        "airlines",
        "airports",
        "flights",
      ];
      const demoFolder = hotelAndFlightFolders.includes(effectiveFolder)
        ? effectiveFolder
        : "hotels";
      const demoData: SimpleMediaItem[] = Array.from({ length: 6 }, (_, i) => ({
        publicId: `${demoFolder}/demo-${i + 1}`,
        url: `https://res.cloudinary.com/demo/image/upload/v1234567890/${demoFolder}/demo-${
          i + 1
        }.jpg`,
        secureUrl: `https://res.cloudinary.com/demo/image/upload/v1234567890/${demoFolder}/demo-${
          i + 1
        }.jpg`,
        format: "jpg",
        width: 800,
        height: 600,
        bytes: 125000,
        folder: demoFolder,
      }));
      setMediaItems(demoData);
      setTotalPages(1);
      setHasNextPage(false);
      setHasPreviousPage(false);
      setCurrentCursor(null);
    } finally {
      setLoading(false);
    }
  };

  // Effect to reset pagination when search or folder changes
  useEffect(() => {
    if (isOpen) {
      setCurrentPage(1);
      setCurrentCursor(null);
      setPageCursors({});
    }
  }, [isOpen, searchQuery, folder]);

  // Effect to fetch data
  useEffect(() => {
    if (isOpen) {
      fetchMediaItems();
    }
  }, [isOpen, searchQuery, currentPage, folder]);

  // Convert value prop to selectedMedia based on mode
  useEffect(() => {
    const convertValueToSelectedMedia = async () => {
      if (!value || value.length === 0) {
        setSelectedMedia([]);
        setSelectedMediaObjects([]);
        return;
      }

      // Check if value is MediaResponse array
      if (
        value.length > 0 &&
        typeof value[0] === "object" &&
        (value[0] as MediaResponse).id !== undefined
      ) {
        // Handle MediaResponse array
        const mediaResponses = value as MediaResponse[];
        const publicIds = mediaResponses.map((media) => media.publicId);
        setSelectedMedia(publicIds);
        setSelectedMediaObjects(mediaResponses);
      } else if (mode === "mediaIds") {
        // Convert mediaIds to publicIds
        const mediaIds = value as number[];
        console.log("Converting mediaIds to publicIds:", mediaIds);
        try {
          const mediaDtos = await mediaService.convertMediaIdsToMediaDtos(
            mediaIds,
            folder
          );
          console.log("Converted mediaDtos:", mediaDtos);
          const publicIds = mediaDtos.map((dto) => dto.publicId);
          setSelectedMedia(publicIds);

          // Create MediaResponse objects from DTOs
          const mediaResponses = mediaDtos.map((dto, index) => ({
            id: dto.id || index + 1,
            mediaId: dto.mediaId || dto.id || index + 1,
            publicId: dto.publicId,
            url: dto.url,
            secureUrl: dto.secureUrl,
            isPrimary: false, // Default value
            displayOrder: 0, // Default value
          }));
          setSelectedMediaObjects(mediaResponses);
        } catch (error) {
          console.error("Error converting mediaIds to publicIds:", error);
          // Fallback: create demo publicIds from mediaIds
          const fallbackPublicIds = mediaIds.map(
            (id) => `${folder}/media-${id}`
          );
          setSelectedMedia(fallbackPublicIds);

          // Create demo MediaResponse objects
          const demoMediaResponses = mediaIds.map((id, index) => ({
            id: id,
            mediaId: id,
            publicId: `${folder}/media-${id}`,
            url: `https://res.cloudinary.com/demo/image/upload/v1234567890/${folder}/media-${id}.jpg`,
            secureUrl: `https://res.cloudinary.com/demo/image/upload/v1234567890/${folder}/media-${id}.jpg`,
            isPrimary: index === 0, // First one as primary
            displayOrder: index,
          }));
          setSelectedMediaObjects(demoMediaResponses);
        }
      } else {
        // Direct publicIds - but check if they're actually numbers (common mistake)
        const values = value as any[];
        if (values.length > 0 && typeof values[0] === "number") {
          console.warn(
            "MediaSelector received numbers but mode is publicIds. Converting to mediaIds mode."
          );
          // Auto-convert to mediaIds mode
          const mediaIds = values as number[];
          try {
            const mediaDtos = await mediaService.convertMediaIdsToMediaDtos(
              mediaIds,
              folder
            );
            const publicIds = mediaDtos.map((dto) => dto.publicId);
            setSelectedMedia(publicIds);

            // Create MediaResponse objects from DTOs
            const mediaResponses = mediaDtos.map((dto, index) => ({
              id: dto.id || index + 1,
              mediaId: dto.mediaId || dto.id || index + 1,
              publicId: dto.publicId,
              url: dto.url,
              secureUrl: dto.secureUrl,
              isPrimary: false, // Default value
              displayOrder: 0, // Default value
            }));
            setSelectedMediaObjects(mediaResponses);
          } catch (error) {
            console.error("Error auto-converting numbers to publicIds:", error);
            const fallbackPublicIds = mediaIds.map(
              (id) => `${folder}/media-${id}`
            );
            setSelectedMedia(fallbackPublicIds);

            // Create demo MediaResponse objects
            const demoMediaResponses = mediaIds.map((id, index) => ({
              id: id,
              mediaId: id,
              publicId: `${folder}/media-${id}`,
              url: `https://res.cloudinary.com/demo/image/upload/v1234567890/${folder}/media-${id}.jpg`,
              secureUrl: `https://res.cloudinary.com/demo/image/upload/v1234567890/${folder}/media-${id}.jpg`,
              isPrimary: index === 0, // First one as primary
              displayOrder: index,
            }));
            setSelectedMediaObjects(demoMediaResponses);
          }
        } else {
          // Normal publicIds
          setSelectedMedia(value as string[]);

          // Create basic MediaResponse objects from publicIds
          const mediaResponses = (value as string[]).map((publicId, index) => ({
            id: index, // Placeholder ID
            mediaId: index, // Placeholder mediaId
            publicId: publicId,
            url: `https://res.cloudinary.com/demo/image/upload/v1234567890/${publicId}`, // Placeholder URL
            secureUrl: `https://res.cloudinary.com/demo/image/upload/v1234567890/${publicId}`, // Placeholder secure URL
            isPrimary: index === 0, // First one as primary
            displayOrder: index,
          }));
          setSelectedMediaObjects(mediaResponses);
        }
      }
    };

    convertValueToSelectedMedia();
  }, [value, mode, folder]);

  const handleUpload = async () => {
    if (!selectedFile) return;

    try {
      setUploading(true);
      const result = await mediaService.uploadMedia(selectedFile, folder);
      toast.success(result.message || "Media uploaded successfully");
      setSelectedFile(null);
      fetchMediaItems();
    } catch (error) {
      console.error("Error uploading media:", error);
      toast.error("Failed to upload media");
    } finally {
      setUploading(false);
    }
  };

  const handleBulkUpload = async () => {
    if (bulkFiles.length === 0) return;

    try {
      setUploading(true);
      const results = await mediaService.bulkUploadMedia(bulkFiles, folder);
      toast.success(`Successfully uploaded ${results.length} media files`);
      setBulkFiles([]);
      fetchMediaItems();
    } catch (error) {
      console.error("Error uploading media:", error);
      toast.error("Failed to upload media");
    } finally {
      setUploading(false);
    }
  };

  const handleUrlUpload = async () => {
    if (!urlInput.trim()) return;

    try {
      setIsUploadingUrl(true);
      const result = await mediaService.uploadMediaFromUrl(urlInput, folder);
      toast.success(result.message || "Media uploaded successfully");
      setUrlInput("");
      fetchMediaItems();
    } catch (error) {
      console.error("Error uploading media from URL:", error);
      toast.error("Failed to upload media from URL");
    } finally {
      setIsUploadingUrl(false);
    }
  };

  const toggleMediaSelection = (
    publicId: string,
    mediaItem?: SimpleMediaItem
  ) => {
    const newSelection = selectedMedia.includes(publicId)
      ? selectedMedia.filter((id) => id !== publicId)
      : selectedMedia.length < maxSelection
      ? [...selectedMedia, publicId]
      : selectedMedia;

    setSelectedMedia(newSelection);

    // Update selected media objects
    if (selectedMedia.includes(publicId)) {
      // Remove from selected objects
      const newObjects = selectedMediaObjects.filter(
        (obj) => obj.publicId !== publicId
      );
      setSelectedMediaObjects(newObjects);
    } else if (selectedMedia.length < maxSelection && mediaItem) {
      // Add to selected objects
      const newMediaResponse: MediaResponse = {
        id: mediaItem.id || Date.now(), // Use actual ID if available, otherwise placeholder
        mediaId: mediaItem.mediaId || mediaItem.id || Date.now(), // Use mediaId if available, otherwise ID, otherwise placeholder
        publicId: mediaItem.publicId,
        url: mediaItem.url,
        secureUrl: mediaItem.secureUrl,
        isPrimary: false, // Will be set later if needed
        displayOrder: selectedMediaObjects.length,
      };
      setSelectedMediaObjects([...selectedMediaObjects, newMediaResponse]);
    }
  };

  const handleConfirmSelection = () => {
    onChange?.(selectedMedia);
    onMediaChange?.(selectedMediaObjects);
    setIsOpen(false);
  };

  const CloudinaryImageComponent = ({
    publicId,
    url,
    className,
  }: {
    publicId: string;
    url?: string;
    className?: string;
  }) => {
    // Debug: log the publicId to see what we're getting
    // console.log('CloudinaryImageComponent received publicId:', publicId, 'url:', url);

    // Check if we have a direct URL from media object and it's already a valid URL
    if (url && (url.startsWith("http://") || url.startsWith("https://"))) {
      // If it's already a full URL, use it directly
      return (
        <img
          src={url}
          alt="Media"
          className={className}
          loading="lazy"
          onError={(e) => {
            console.error("Image failed to load:", url);
            // Show placeholder on error
            e.currentTarget.style.display = "none";
            const placeholder = document.createElement("div");
            placeholder.className = `${className} bg-gray-200 flex items-center justify-center border`;
            placeholder.innerHTML =
              '<span class="text-gray-500 text-xs">Image Error</span>';
            e.currentTarget.parentNode?.appendChild(placeholder);
          }}
        />
      );
    }

    // Check if publicId looks like a database ID (just numbers) or invalid
    if (!publicId || publicId === "undefined" || publicId === "null") {
      // console.warn('Invalid publicId:', publicId);
      return (
        <div
          className={`${className} bg-gray-200 flex items-center justify-center border`}
        >
          <span className="text-gray-500 text-xs">No Image</span>
        </div>
      );
    }

    // Check if publicId is just a number (likely a mediaId that wasn't converted)
    if (/^\d+$/.test(publicId)) {
      // console.warn('PublicId appears to be a number (mediaId):', publicId);
      // Try to find the media item with this ID and use its publicId
      const mediaItem = mediaItems.find(
        (item) =>
          item.publicId.includes(publicId) ||
          item.publicId.endsWith(`-${publicId}`)
      );
      if (mediaItem) {
        // console.log('Found media item:', mediaItem);
        publicId = mediaItem.publicId;
      } else {
        // Create a demo publicId for testing
        const hotelAndFlightFolders = [
          "hotels",
          "rooms",
          "amenities",
          "room-types",
          "airlines",
          "airports",
          "flights",
        ];
        const demoFolder = hotelAndFlightFolders.includes(effectiveFolder)
          ? effectiveFolder
          : "hotels";
        publicId = `${demoFolder}/demo-${publicId}`;
        // console.log('Created demo publicId:', publicId);
      }
    }

    if (process.env.NEXT_PUBLIC_CLOUDINARY_CLOUD_NAME) {
      // Directly construct Cloudinary URL for better control
      const cloudName = process.env.NEXT_PUBLIC_CLOUDINARY_CLOUD_NAME;
      const imageUrl = `https://res.cloudinary.com/${cloudName}/image/upload/w_200,h_150,c_fill,q_auto,f_auto/${publicId}`;

      return (
        <img
          src={imageUrl}
          alt="Media"
          className={className}
          loading="lazy"
          onError={(e) => {
            console.error("Image failed to load:", imageUrl);
            // Show placeholder on error
            e.currentTarget.style.display = "none";
            const placeholder = document.createElement("div");
            placeholder.className = `${className} bg-gray-200 flex items-center justify-center border`;
            placeholder.innerHTML =
              '<span class="text-gray-500 text-xs">Image Error</span>';
            e.currentTarget.parentNode?.appendChild(placeholder);
          }}
        />
      );
    }

    // Use the media service to get optimized URL
    const optimizedUrl = mediaService.getOptimizedUrl(
      `/api/media/${publicId}`,
      {
        width: 200,
        height: 150,
        crop: "fill",
        quality: "auto",
      }
    );

    return (
      <img
        src={optimizedUrl}
        alt="Media"
        className={className}
        loading="lazy"
        onError={(e) => {
          console.error("Image failed to load:", optimizedUrl);
          // Show placeholder on error
          e.currentTarget.style.display = "none";
          const placeholder = document.createElement("div");
          placeholder.className = `${className} bg-gray-200 flex items-center justify-center border`;
          placeholder.innerHTML =
            '<span class="text-gray-500 text-xs">Image Error</span>';
          e.currentTarget.parentNode?.appendChild(placeholder);
        }}
      />
    );
  };

  const formatFileSize = (bytes: number) => {
    if (bytes === 0) return "0 Bytes";
    const k = 1024;
    const sizes = ["Bytes", "KB", "MB", "GB"];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + " " + sizes[i];
  };

  return (
    <div className={className}>
      {/* Selected Media Preview - Show only in form dialogs */}
      {selectedMedia.length > 0 && (
        <div className="mb-4">
          <Label className="text-sm font-medium">
            Selected Media ({selectedMedia.length}/{maxSelection})
          </Label>
          <div className="grid grid-cols-3 gap-2 mt-2">
            {selectedMediaObjects.map((media) => (
              <div key={media.publicId || media.id} className="relative group">
                <CloudinaryImageComponent
                  publicId={media.publicId}
                  url={media.url || media.secureUrl}
                  className="w-full h-20 object-cover rounded-md"
                />
                {/* Primary indicator for preview */}
                {primaryImage === media.publicId && (
                  <div className="absolute top-1 left-1 bg-orange-500 text-white rounded px-1 text-xs">
                    PRIMARY
                  </div>
                )}
                <Button
                  size="sm"
                  variant="destructive"
                  className="absolute top-1 right-1 h-6 w-6 p-0 opacity-0 group-hover:opacity-100"
                  onClick={() => toggleMediaSelection(media.publicId)}
                >
                  <Trash2 className="h-3 w-3" />
                </Button>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Media Selector Button */}
      <Button
        type="button"
        variant="outline"
        onClick={() => setIsOpen(true)}
        className="w-full"
      >
        <Image className="h-4 w-4 mr-2" />
        {selectedMedia.length > 0
          ? `${selectedMedia.length} selected`
          : "Select Media"}
        {folder && (
          <Badge variant="secondary" className="ml-2">
            {folder}
          </Badge>
        )}
      </Button>

      {/* Media Selection Dialog */}
      <Dialog open={isOpen} onOpenChange={setIsOpen}>
        <DialogContent className="max-w-5xl max-h-[90vh] overflow-hidden flex flex-col">
          <DialogHeader>
            <DialogTitle className="flex items-center gap-2">
              <Image className="h-5 w-5" />
              Select Media -{" "}
              {effectiveFolder.charAt(0).toUpperCase() +
                effectiveFolder.slice(1)}
            </DialogTitle>
          </DialogHeader>

          <div className="flex flex-col space-y-4 flex-1 overflow-hidden">
            {/* Controls */}
            <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
              <div className="space-y-2">
                <Label htmlFor="search">Search</Label>
                <div className="relative">
                  <Search className="absolute left-2 top-2.5 h-4 w-4 text-muted-foreground" />
                  <Input
                    id="search"
                    placeholder="Search media..."
                    value={searchQuery}
                    onChange={(e) => setSearchQuery(e.target.value)}
                    className="pl-8"
                  />
                </div>
              </div>

              {allowUpload && (
                <div className="space-y-2">
                  <Label htmlFor="upload">Upload New</Label>
                  <Input
                    id="upload"
                    type="file"
                    accept="image/*,video/*"
                    onChange={(e) =>
                      setSelectedFile(e.target.files?.[0] || null)
                    }
                  />
                </div>
              )}

              <div className="space-y-2">
                <Label>&nbsp;</Label>
                <Button
                  onClick={handleUpload}
                  disabled={!selectedFile || uploading}
                  className="w-full"
                >
                  <Upload className="h-4 w-4 mr-2" />
                  {uploading ? "Uploading..." : "Upload"}
                </Button>
              </div>

              {allowUrlInput && (
                <>
                  <div className="space-y-2 md:col-span-2">
                    <Label htmlFor="url-input">Or Enter Media URL</Label>
                    <div className="flex gap-2">
                      <Input
                        id="url-input"
                        placeholder="Enter image or video URL..."
                        value={urlInput}
                        onChange={(e) => setUrlInput(e.target.value)}
                        disabled={isUploadingUrl}
                        className="flex-1"
                      />
                      <Button
                        onClick={handleUrlUpload}
                        disabled={!urlInput.trim() || isUploadingUrl}
                        variant="outline"
                        className="h-[38px]"
                      >
                        <Link className="h-4 w-4" />
                      </Button>
                    </div>
                  </div>

                  <div className="space-y-2">
                    <Label htmlFor="bulk-upload">Bulk Upload</Label>
                    <div className="flex gap-2">
                      <Input
                        id="bulk-upload"
                        type="file"
                        accept="image/*,video/*"
                        multiple
                        onChange={(e) => {
                          const files = Array.from(e.target.files || []);
                          if (files.length > maxSelection) {
                            toast.warning(
                              `You can only select up to ${maxSelection} files`
                            );
                            return;
                          }
                          setBulkFiles(files);
                        }}
                        disabled={uploading}
                      />
                    </div>
                    {bulkFiles.length > 0 && (
                      <div className="text-sm text-gray-600">
                        {bulkFiles.length} file(s) selected
                      </div>
                    )}
                  </div>

                  <div className="space-y-2">
                    <Label>&nbsp;</Label>
                    <Button
                      onClick={handleBulkUpload}
                      disabled={bulkFiles.length === 0 || uploading}
                      className="w-full"
                    >
                      <Upload className="h-4 w-4 mr-2" />
                      {uploading
                        ? "Uploading..."
                        : `Upload ${bulkFiles.length} files`}
                    </Button>
                  </div>
                </>
              )}
            </div>

            {/* Media Grid */}
            <div className="flex-1 overflow-auto">
              <Card>
                <CardContent className="p-4">
                  {loading ? (
                    <div className="flex justify-center items-center h-40">
                      <p className="text-gray-500">Loading media...</p>
                    </div>
                  ) : mediaItems.length === 0 ? (
                    <div className="flex justify-center items-center h-40">
                      <p className="text-gray-500">
                        No media found in {effectiveFolder} folder
                      </p>
                    </div>
                  ) : (
                    <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-4">
                      {mediaItems.map((media) => {
                        const isSelected = selectedMedia.includes(
                          media.publicId
                        );
                        return (
                          <div
                            key={media.publicId}
                            className={`relative cursor-pointer border-2 rounded-lg overflow-hidden transition-all ${
                              isSelected
                                ? "border-blue-500 bg-blue-50 ring-2 ring-blue-200"
                                : "border-gray-200 hover:border-gray-300"
                            }`}
                            onClick={() => {
                              if (
                                selectedMedia.length < maxSelection ||
                                isSelected
                              ) {
                                toggleMediaSelection(media.publicId, media);
                              } else {
                                toast.warning(
                                  `Maximum ${maxSelection} media can be selected`
                                );
                              }
                            }}
                          >
                            <CloudinaryImageComponent
                              publicId={media.publicId}
                              url={media.url || media.secureUrl}
                              className="w-full h-32 object-cover"
                            />

                            {/* Selection indicator */}
                            {isSelected && (
                              <div className="absolute top-2 right-2 bg-blue-500 text-white rounded-full p-1 shadow-lg">
                                <Check className="h-3 w-3" />
                              </div>
                            )}

                            {/* Primary image indicator */}
                            {primaryImage === media.publicId && (
                              <div className="absolute top-2 left-2 bg-orange-500 text-white rounded px-2 py-1 text-xs font-semibold shadow-lg">
                                PRIMARY
                              </div>
                            )}

                            {/* Primary image button - only show if selected */}
                            {isSelected && onPrimaryChange && (
                              <div className="absolute bottom-2 left-2">
                                <button
                                  onClick={(e) => {
                                    e.stopPropagation();
                                    onPrimaryChange(
                                      primaryImage === media.publicId
                                        ? null
                                        : media.publicId
                                    );
                                  }}
                                  className={`text-xs px-2 py-1 rounded ${
                                    primaryImage === media.publicId
                                      ? "bg-orange-500 text-white"
                                      : "bg-white text-gray-700 border border-gray-300 hover:bg-gray-50"
                                  }`}
                                >
                                  {primaryImage === media.publicId
                                    ? "Primary"
                                    : "Set Primary"}
                                </button>
                              </div>
                            )}

                            {/* Media info */}
                            <div className="p-2 bg-white">
                              <div className="flex items-center justify-between mb-1">
                                <Badge variant="outline" className="text-xs">
                                  {media.format.toUpperCase()}
                                </Badge>
                              </div>

                              <p className="text-xs text-gray-600 truncate">
                                {media.publicId.split("/").pop()}
                              </p>

                              <div className="flex justify-between items-center text-xs text-gray-500 mt-1">
                                <span>{formatFileSize(media.bytes)}</span>
                                {media.width && media.height && (
                                  <span>
                                    {media.width}×{media.height}
                                  </span>
                                )}
                              </div>
                            </div>
                          </div>
                        );
                      })}
                    </div>
                  )}
                </CardContent>
              </Card>
            </div>

            {/* Pagination */}
            {(hasNextPage || hasPreviousPage || currentPage > 1) && (
              <div className="flex justify-center items-center space-x-2 py-2">
                <Button
                  variant="outline"
                  size="sm"
                  onClick={async () => {
                    // Go back to first page and reset cursor state
                    setCurrentPage(1);
                    setCurrentCursor(null);
                    setPageCursors({});
                    await fetchMediaItems();
                  }}
                  disabled={currentPage === 1}
                >
                  First
                </Button>
                <span className="text-sm text-gray-600">
                  Page {currentPage}{" "}
                  {totalPages > 0 ? `of ${totalPages}` : "(cursor-based)"}
                </span>
                <Button
                  variant="outline"
                  size="sm"
                  onClick={async () => {
                    if (hasNextPage) {
                      // Move to next page using cursor
                      setCurrentPage((prev) => prev + 1);
                      await fetchMediaItems();
                    }
                  }}
                  disabled={!hasNextPage}
                >
                  Next
                </Button>
              </div>
            )}

            {/* Footer */}
            <div className="flex justify-between items-center border-t pt-4">
              <p className="text-sm text-gray-600">
                {selectedMedia.length} of {maxSelection} selected
              </p>
              <div className="space-x-2">
                <Button variant="outline" onClick={() => setIsOpen(false)}>
                  Cancel
                </Button>
                <Button onClick={handleConfirmSelection}>
                  Confirm Selection
                </Button>
              </div>
            </div>
          </div>
        </DialogContent>
      </Dialog>
    </div>
  );
}
