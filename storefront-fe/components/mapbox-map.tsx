"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import mapboxgl from "mapbox-gl";
import { createRoot, type Root } from "react-dom/client";
import { Plane, Hotel as HotelIcon } from "lucide-react";
import { cn } from "@/lib/utils";

// Import Mapbox CSS
import "mapbox-gl/dist/mapbox-gl.css";
import { env } from "@/env.mjs";

export interface MapLocation {
  id: string;
  name: string;
  latitude: number;
  longitude: number;
  type?: "hotel" | "airport" | "destination" | "custom";
  description?: string;
  price?: string;
  image?: string;
}

export interface MapJourney {
  id: string;
  origin: {
    latitude: number;
    longitude: number;
  };
  destination: {
    latitude: number;
    longitude: number;
  };
  color?: string;
  travelMode?: "flight" | "drive" | "train" | "custom";
  animate?: boolean;
  pathCoordinates?: [number, number][];
  markerLabel?: string;
  durationMs?: number;
}

export interface MapboxMapProps {
  className?: string;
  locations?: MapLocation[];
  journeys?: MapJourney[];
  center?: [number, number]; // [longitude, latitude]
  zoom?: number;
  style?: string;
  showControls?: boolean;
  onLocationClick?: (location: MapLocation) => void;
  onMapLoad?: (map: mapboxgl.Map) => void;
  interactive?: boolean;
  height?: string;
}

type JourneyController = {
  id: string;
  map: mapboxgl.Map;
  marker?: mapboxgl.Marker;
  markerRoot?: Root;
  animationFrame: number | null;
  coordinates: [number, number][];
  cleanup: () => void;
  durationMs: number;
  startTimestamp?: number;
  currentIndex: number;
  layerId: string;
  sourceId: string;
  travelMode?: MapJourney["travelMode"];
};

const DEFAULT_CENTER: [number, number] = [
  106.80337596151362, 10.870060732280548,
];
const DEFAULT_JOURNEY_DURATION = 15000;
const DEFAULT_JOURNEY_STEPS = 420;

const toRadians = (deg: number) => (deg * Math.PI) / 180;
const toDegrees = (rad: number) => (rad * 180) / Math.PI;

const calculateBearing = (from: [number, number], to: [number, number]) => {
  if (!from || !to) return 0;
  const [fromLng, fromLat] = from;
  const [toLng, toLat] = to;
  if (fromLng === toLng && fromLat === toLat) return 0;

  const φ1 = toRadians(fromLat);
  const φ2 = toRadians(toLat);
  const Δλ = toRadians(toLng - fromLng);
  const y = Math.sin(Δλ) * Math.cos(φ2);
  const x =
    Math.cos(φ1) * Math.sin(φ2) - Math.sin(φ1) * Math.cos(φ2) * Math.cos(Δλ);
  const θ = Math.atan2(y, x);
  return (toDegrees(θ) + 360) % 360;
};

const isFiniteCoordinate = (value: unknown): value is number =>
  typeof value === "number" && Number.isFinite(value);

const computeControlPoint = (
  origin: [number, number],
  destination: [number, number]
) => {
  const [originLng, originLat] = origin;
  const [destinationLng, destinationLat] = destination;
  const midLng = (originLng + destinationLng) / 2;
  const midLat = (originLat + destinationLat) / 2;
  const deltaLng = destinationLng - originLng;
  const deltaLat = destinationLat - originLat;
  const distance = Math.sqrt(deltaLng * deltaLng + deltaLat * deltaLat) || 1;
  const offsetMagnitude = distance * 0.35;
  const offsetLng = (-deltaLat / distance) * offsetMagnitude;
  const offsetLat = (deltaLng / distance) * offsetMagnitude;
  return [midLng + offsetLng, midLat + offsetLat] as [number, number];
};

const generateArc = (
  origin: [number, number],
  destination: [number, number],
  steps = DEFAULT_JOURNEY_STEPS
) => {
  if (
    !isFiniteCoordinate(origin[0]) ||
    !isFiniteCoordinate(origin[1]) ||
    !isFiniteCoordinate(destination[0]) ||
    !isFiniteCoordinate(destination[1])
  ) {
    return [] as [number, number][];
  }

  if (origin[0] === destination[0] && origin[1] === destination[1]) {
    return [origin, destination];
  }

  const control = computeControlPoint(origin, destination);
  const coordinates: [number, number][] = [];

  for (let step = 0; step <= steps; step += 1) {
    const t = step / steps;
    const oneMinusT = 1 - t;
    const lng =
      oneMinusT * oneMinusT * origin[0] +
      2 * oneMinusT * t * control[0] +
      t * t * destination[0];
    const lat =
      oneMinusT * oneMinusT * origin[1] +
      2 * oneMinusT * t * control[1] +
      t * t * destination[1];
    coordinates.push([lng, lat]);
  }

  return coordinates;
};

interface FlightMarkerProps {
  bearing: number;
  label?: string;
  status?: "active" | "idle";
}

const FlightMarker = ({
  bearing,
  label,
  status = "active",
}: FlightMarkerProps) => {
  return (
    <div
      className={`flight-marker-root ${
        status === "idle" ? "flight-marker-root--idle" : ""
      }`}
    >
      <div
        className="flight-marker-plane"
        style={{ transform: `rotate(${bearing}deg)` }}
      >
        <Plane className="h-4 w-4" />
      </div>
      {label ? <span className="flight-marker-label">{label}</span> : null}
    </div>
  );
};

interface HotelMarkerProps {
  title: string;
  price?: string;
}

const HotelMarker = ({ title, price }: HotelMarkerProps) => {
  return (
    <div className="hotel-marker-root" title={title}>
      <div className="hotel-marker-icon">
        <HotelIcon className="h-4 w-4" />
      </div>
      {price ? <span className="hotel-marker-price">{price}</span> : null}
    </div>
  );
};

const markerIconForType = (type?: MapLocation["type"]) => {
  switch (type) {
    case "hotel":
      return "fas fa-bed";
    case "airport":
      return "fas fa-plane";
    case "destination":
      return "fas fa-map-marker-alt";
    default:
      return "fas fa-map-marker-alt";
  }
};

const markerColorForType = (type?: MapLocation["type"]) => {
  switch (type) {
    case "hotel":
      return "marker-hotel";
    case "airport":
      return "marker-airport";
    case "destination":
      return "marker-destination";
    default:
      return "marker-default";
  }
};

const buildMarkerHTML = (location: MapLocation): string => {
  const iconClass = markerIconForType(location.type);
  const colorClass = markerColorForType(location.type);

  return `
      <div class="marker-container ${colorClass} hover:scale-110 transition-transform cursor-pointer">
        <div class="marker-content">
          <i class="${iconClass}"></i>
          ${
            location.price
              ? `<span class="marker-price">${location.price}</span>`
              : ""
          }
        </div>
      </div>
    `;
};

const buildPopupHTML = (location: MapLocation): string => {
  return `
      <div class="popup-content">
        ${
          location.image
            ? `<img src="${location.image}" alt="${location.name}" class="popup-image" />`
            : ""
        }
        <div class="popup-body">
          <h3 class="popup-title">${location.name}</h3>
          ${
            location.description
              ? `<p class="popup-description">${location.description}</p>`
              : ""
          }
          ${
            location.price
              ? `<div class="popup-price">${location.price}</div>`
              : ""
          }
        </div>
      </div>
    `;
};

const createExtrusionPolygon = (
  longitude: number,
  latitude: number,
  size = 0.00035
) => {
  const lngOffset = size;
  const latOffset = size * Math.cos((latitude * Math.PI) / 180);
  return [
    [longitude - lngOffset, latitude - latOffset],
    [longitude + lngOffset, latitude - latOffset],
    [longitude + lngOffset, latitude + latOffset],
    [longitude - lngOffset, latitude + latOffset],
    [longitude - lngOffset, latitude - latOffset],
  ] as [number, number][];
};

export function MapboxMap({
  className,
  locations = [],
  journeys = [],
  center = DEFAULT_CENTER,
  zoom = 10,
  style = "mapbox://styles/phamduyhuy/cmgnvl0ec00ud01se98ju3a80",
  showControls = true,
  onLocationClick,
  onMapLoad,
  interactive = true,
  height = "100vh",
}: MapboxMapProps) {
  const mapContainer = useRef<HTMLDivElement>(null);
  const map = useRef<mapboxgl.Map | null>(null);
  const markers = useRef<Array<{ marker: mapboxgl.Marker; root?: Root }>>([]);
  const journeyControllers = useRef<Record<string, JourneyController>>({});
  const hotelStructures = useRef<
    Record<string, { sourceId: string; layerId: string }>
  >({});
  const [isLoaded, setIsLoaded] = useState(false);

  const applyGlobeProjection = useCallback(() => {
    const mapInstance = map.current;
    if (!mapInstance || typeof mapInstance.setProjection !== "function") {
      return;
    }

    try {
      mapInstance.setProjection("globe");
    } catch (error) {
      console.warn("Failed to apply globe projection", error);
    }
  }, []);

  const addHotelExtrusion = useCallback((location: MapLocation) => {
    if (!map.current || !location.id) return;

    const mapInstance = map.current;
    if (!mapInstance.isStyleLoaded()) return;
    const sourceId = `hotel-structure-source-${location.id}`;
    const layerId = `hotel-structure-layer-${location.id}`;

    if (mapInstance.getLayer(layerId)) {
      mapInstance.removeLayer(layerId);
    }

    if (mapInstance.getSource(sourceId)) {
      mapInstance.removeSource(sourceId);
    }

    const coordinates = createExtrusionPolygon(
      location.longitude,
      location.latitude
    );

    const feature: GeoJSON.Feature<GeoJSON.Polygon> = {
      type: "Feature",
      properties: {
        name: location.name,
        base_height: 0,
        height: 80,
      },
      geometry: {
        type: "Polygon",
        coordinates: [coordinates],
      },
    };

    mapInstance.addSource(sourceId, {
      type: "geojson",
      data: {
        type: "FeatureCollection",
        features: [feature],
      },
    });

    mapInstance.addLayer({
      id: layerId,
      type: "fill-extrusion",
      source: sourceId,
      paint: {
        "fill-extrusion-color": "#0ea5e9",
        "fill-extrusion-height": ["get", "height"],
        "fill-extrusion-base": ["get", "base_height"],
        "fill-extrusion-opacity": 0.8,
      },
    });

    hotelStructures.current[location.id] = { sourceId, layerId };
  }, []);

  // Initialize map
  useEffect(() => {
    if (!mapContainer.current || map.current) return;

    // Get API key from environment variables
    const apiKey = env.NEXT_PUBLIC_MAPBOX_ACCESS_TOKEN;
    if (!apiKey) {
      console.error(
        "Mapbox API key not found. Please set NEXT_PUBLIC_MAPBOX_ACCESS_TOKEN in your environment variables."
      );
      return;
    }

    mapboxgl.accessToken = apiKey;
    map.current = new mapboxgl.Map({
      container: mapContainer.current,
      style,
      center,
      zoom,
      interactive,
    });

    map.current.on("load", () => {
      applyGlobeProjection();
      setIsLoaded(true);
      if (onMapLoad && map.current) {
        onMapLoad(map.current);
      }
    });

    // Add navigation controls if enabled
    if (showControls) {
      map.current.addControl(new mapboxgl.NavigationControl());
      map.current.addControl(new mapboxgl.FullscreenControl());
    }

    return () => {
      setIsLoaded(false);
      markers.current.forEach(({ marker, root }) => {
        marker.remove();
        root?.unmount();
      });
      markers.current = [];
      Object.values(hotelStructures.current).forEach(
        ({ sourceId, layerId }) => {
          if (map.current?.getLayer(layerId)) {
            map.current.removeLayer(layerId);
          }
          if (map.current?.getSource(sourceId)) {
            map.current.removeSource(sourceId);
          }
        }
      );
      hotelStructures.current = {};
      Object.values(journeyControllers.current).forEach((controller) =>
        controller.cleanup()
      );
      journeyControllers.current = {};
      if (map.current) {
        map.current.remove();
        map.current = null;
      }
    };
  }, [
    center,
    zoom,
    style,
    showControls,
    interactive,
    onMapLoad,
    applyGlobeProjection,
  ]);

  useEffect(() => {
    if (!map.current) return;

    setIsLoaded(false);
    const mapInstance = map.current;

    const handleStyleLoad = () => {
      applyGlobeProjection();
      setIsLoaded(true);
    };

    mapInstance.once("styledata", handleStyleLoad);
    mapInstance.setStyle(style);

    return () => {
      mapInstance.off("styledata", handleStyleLoad);
    };
  }, [style, applyGlobeProjection]);

  useEffect(() => {
    if (!map.current || !isLoaded) return;
    const mapInstance = map.current;
    mapInstance.flyTo({
      center,
      zoom,
      speed: 0.9,
      curve: 1.2,
      essential: true,
    });
  }, [center, zoom, isLoaded]);

  // Update markers when locations change
  useEffect(() => {
    if (!map.current || !isLoaded) return;
    if (!map.current.isStyleLoaded()) return;

    // Clear existing markers
    markers.current.forEach(({ marker, root }) => {
      marker.remove();
      root?.unmount();
    });
    markers.current = [];
    Object.values(hotelStructures.current).forEach(({ sourceId, layerId }) => {
      if (map.current?.getLayer(layerId)) {
        map.current.removeLayer(layerId);
      }
      if (map.current?.getSource(sourceId)) {
        map.current.removeSource(sourceId);
      }
    });
    hotelStructures.current = {};

    // Add new markers
    locations.forEach((location) => {
      if (!map.current) return;

      // Create marker element
      const markerElement = document.createElement("div");
      let markerRoot: Root | undefined;

      if (location.type === "hotel") {
        markerElement.className = "custom-marker hotel-custom-marker";
        markerRoot = createRoot(markerElement);
        markerRoot.render(
          <HotelMarker title={location.name} price={location.price} />
        );
      } else {
        markerElement.className = "custom-marker";
        markerElement.innerHTML = buildMarkerHTML(location);
      }

      // Create marker
      const marker = new mapboxgl.Marker({
        element: markerElement,
        anchor: "bottom",
      })
        .setLngLat([location.longitude, location.latitude])
        .addTo(map.current);

      // Add popup
      const popup = new mapboxgl.Popup({
        offset: 25,
        closeButton: true,
        closeOnClick: false,
      }).setHTML(buildPopupHTML(location));

      marker.setPopup(popup);

      // Add click handler
      markerElement.addEventListener("click", () => {
        if (onLocationClick) {
          onLocationClick(location);
        }
      });

      if (location.type === "hotel") {
        addHotelExtrusion(location);
      }

      markers.current.push({ marker, root: markerRoot });
    });

    // Fit map to markers if multiple locations
    if (locations.length > 1 && map.current) {
      const coordinates = locations.map(
        (loc) => [loc.longitude, loc.latitude] as [number, number]
      );
      const bounds = coordinates.reduce((bounds, coord) => {
        return bounds.extend(coord);
      }, new mapboxgl.LngLatBounds(coordinates[0], coordinates[0]));

      map.current.fitBounds(bounds, { padding: 50 });
    }
  }, [locations, isLoaded, onLocationClick, addHotelExtrusion]);

  // Effect to draw journeys with animated flight routes
  useEffect(() => {
    if (!map.current || !isLoaded) return;
    if (!map.current.isStyleLoaded()) return;

    const mapInstance = map.current;
    const nextJourneyIds = new Set(journeys.map((journey) => journey.id));

    // Clean up controllers that are no longer required
    Object.entries(journeyControllers.current).forEach(
      ([journeyId, controller]) => {
        if (!nextJourneyIds.has(journeyId)) {
          controller.cleanup();
          delete journeyControllers.current[journeyId];
        }
      }
    );

    const createdControllers: JourneyController[] = [];

    journeys.forEach((journey) => {
      // Remove any existing controller so we can recreate with latest configuration
      if (journeyControllers.current[journey.id]) {
        journeyControllers.current[journey.id]?.cleanup();
        delete journeyControllers.current[journey.id];
      }

      const origin: [number, number] = [
        journey.origin.longitude,
        journey.origin.latitude,
      ];
      const destination: [number, number] = [
        journey.destination.longitude,
        journey.destination.latitude,
      ];

      if (
        !isFiniteCoordinate(origin[0]) ||
        !isFiniteCoordinate(origin[1]) ||
        !isFiniteCoordinate(destination[0]) ||
        !isFiniteCoordinate(destination[1])
      ) {
        return;
      }

      const coordinates =
        journey.pathCoordinates && journey.pathCoordinates.length > 1
          ? journey.pathCoordinates
          : generateArc(origin, destination);

      if (coordinates.length < 2) {
        return;
      }

      const travelMode = journey.travelMode ?? "flight";
      const shouldAnimate = journey.animate ?? travelMode === "flight";
      const durationMs = journey.durationMs ?? DEFAULT_JOURNEY_DURATION;
      const color = journey.color || "#2563eb";

      const sourceId = `journey-source-${journey.id}`;
      const layerId = `journey-layer-${journey.id}`;

      if (mapInstance.getLayer(layerId)) {
        mapInstance.removeLayer(layerId);
      }

      if (mapInstance.getSource(sourceId)) {
        mapInstance.removeSource(sourceId);
      }

      mapInstance.addSource(sourceId, {
        type: "geojson",
        data: {
          type: "Feature",
          geometry: {
            type: "LineString",
            coordinates,
          },
          properties: {},
        },
      });

      mapInstance.addLayer({
        id: layerId,
        type: "line",
        source: sourceId,
        layout: {
          "line-join": "round",
          "line-cap": "round",
        },
        paint: {
          "line-color": color,
          "line-width": travelMode === "flight" ? 3 : 2,
          "line-opacity": 0.85,
          "line-dasharray": travelMode === "flight" ? [1, 1.4] : [2, 2],
          "line-blur": 0.2,
        },
      });

      if (travelMode !== "flight") {
        const controller: JourneyController = {
          id: journey.id,
          map: mapInstance,
          animationFrame: null,
          coordinates,
          cleanup: () => {
            if (mapInstance.getLayer(layerId)) {
              mapInstance.removeLayer(layerId);
            }
            if (mapInstance.getSource(sourceId)) {
              mapInstance.removeSource(sourceId);
            }
          },
          durationMs,
          currentIndex: coordinates.length - 1,
          layerId,
          sourceId,
          travelMode,
        };

        journeyControllers.current[journey.id] = controller;
        return;
      }

      const markerElement = document.createElement("div");
      markerElement.className = "journey-flight-marker";
      const markerRoot = createRoot(markerElement);
      const initialBearing = calculateBearing(
        coordinates[0],
        coordinates[1] ?? coordinates[0]
      );
      markerRoot.render(
        <FlightMarker
          bearing={initialBearing}
          label={journey.markerLabel}
          status="active"
        />
      );

      const marker = new mapboxgl.Marker({
        element: markerElement,
        anchor: "center",
      })
        .setLngLat(coordinates[0])
        .addTo(mapInstance);

      const controller: JourneyController = {
        id: journey.id,
        map: mapInstance,
        marker,
        markerRoot,
        animationFrame: null,
        coordinates,
        cleanup: () => {
          if (controller.animationFrame) {
            cancelAnimationFrame(controller.animationFrame);
          }
          controller.marker?.remove();
          controller.markerRoot?.unmount();
          if (mapInstance.getLayer(layerId)) {
            mapInstance.removeLayer(layerId);
          }
          if (mapInstance.getSource(sourceId)) {
            mapInstance.removeSource(sourceId);
          }
        },
        durationMs,
        startTimestamp: undefined,
        currentIndex: 0,
        layerId,
        sourceId,
        travelMode: journey.travelMode,
      };

      if (!shouldAnimate) {
        const lastIndex = coordinates.length - 1;
        const last = coordinates[lastIndex];
        const prev = coordinates[lastIndex - 1] ?? last;
        controller.marker?.setLngLat(last);
        controller.markerRoot?.render(
          <FlightMarker
            bearing={calculateBearing(prev, last)}
            label={journey.markerLabel}
            status="idle"
          />
        );
        journeyControllers.current[journey.id] = controller;
        createdControllers.push(controller);
        return;
      }

      const animate = (timestamp: number) => {
        if (!controller.startTimestamp) {
          controller.startTimestamp = timestamp;
        }

        const elapsed = timestamp - controller.startTimestamp;
        const progress = Math.min(elapsed / controller.durationMs, 1);
        const targetIndex = Math.min(
          coordinates.length - 1,
          Math.floor(progress * (coordinates.length - 1))
        );

        if (targetIndex !== controller.currentIndex) {
          const current = coordinates[targetIndex];
          const next = coordinates[targetIndex + 1] ?? coordinates[targetIndex];
          controller.marker?.setLngLat(current);
          controller.markerRoot?.render(
            <FlightMarker
              bearing={calculateBearing(current, next)}
              label={journey.markerLabel}
              status={progress >= 1 ? "idle" : "active"}
            />
          );
          controller.currentIndex = targetIndex;
        }

        if (progress < 1) {
          controller.animationFrame = requestAnimationFrame(animate);
        } else {
          controller.animationFrame = null;
        }
      };

      controller.animationFrame = requestAnimationFrame(animate);

      journeyControllers.current[journey.id] = controller;
      createdControllers.push(controller);
    });

    if (journeys.length > 0) {
      const bounds = journeys.reduce((acc, journey) => {
        if (
          isFiniteCoordinate(journey.origin.longitude) &&
          isFiniteCoordinate(journey.origin.latitude)
        ) {
          acc.extend([journey.origin.longitude, journey.origin.latitude]);
        }
        if (
          isFiniteCoordinate(journey.destination.longitude) &&
          isFiniteCoordinate(journey.destination.latitude)
        ) {
          acc.extend([
            journey.destination.longitude,
            journey.destination.latitude,
          ]);
        }
        return acc;
      }, new mapboxgl.LngLatBounds());

      if (bounds && !bounds.isEmpty()) {
        mapInstance.fitBounds(bounds, {
          padding: 80,
          maxZoom: 7.5,
          duration: 1200,
        });
      }
    }
  }, [journeys, isLoaded]);

  // Public methods
  const flyTo = (longitude: number, latitude: number, zoom?: number) => {
    if (map.current) {
      map.current.flyTo({
        center: [longitude, latitude],
        zoom: zoom || map.current.getZoom(),
        essential: true,
      });
    }
  };

  const addLocation = (location: MapLocation) => {
    if (!map.current || !isLoaded) return;

    const markerElement = document.createElement("div");
    let markerRoot: Root | undefined;

    if (location.type === "hotel") {
      markerElement.className = "custom-marker hotel-custom-marker";
      markerRoot = createRoot(markerElement);
      markerRoot.render(
        <HotelMarker title={location.name} price={location.price} />
      );
    } else {
      markerElement.className = "custom-marker";
      markerElement.innerHTML = buildMarkerHTML(location);
    }

    const marker = new mapboxgl.Marker({
      element: markerElement,
      anchor: "bottom",
    })
      .setLngLat([location.longitude, location.latitude])
      .addTo(map.current);

    const popup = new mapboxgl.Popup({
      offset: 25,
      closeButton: true,
      closeOnClick: false,
    }).setHTML(buildPopupHTML(location));

    marker.setPopup(popup);

    markerElement.addEventListener("click", () => {
      if (onLocationClick) {
        onLocationClick(location);
      }
    });

    if (location.type === "hotel") {
      addHotelExtrusion(location);
    }

    markers.current.push({ marker, root: markerRoot });
  };

  return (
    <div className={cn("relative w-full h-full", className)} style={{ height }}>
      <div ref={mapContainer} className="w-full h-full" />

      {/* Custom CSS for markers */}
      <style jsx global>{`
        .custom-marker {
          cursor: pointer;
        }

        .hotel-custom-marker {
          pointer-events: auto;
        }

        .hotel-marker-root {
          display: flex;
          flex-direction: column;
          align-items: center;
          gap: 4px;
          transform: translate(-50%, -100%);
        }

        .hotel-marker-icon {
          display: flex;
          align-items: center;
          justify-content: center;
          width: 36px;
          height: 36px;
          border-radius: 12px;
          background: rgba(20, 184, 166, 0.95);
          color: #ecfeff;
          box-shadow: 0 8px 20px rgba(13, 148, 136, 0.35);
          border: 2px solid #ecfeff;
        }

        .hotel-marker-price {
          padding: 2px 8px;
          border-radius: 9999px;
          background: rgba(15, 23, 42, 0.88);
          color: #f8fafc;
          font-size: 10px;
          font-weight: 600;
          letter-spacing: 0.02em;
          white-space: nowrap;
          box-shadow: 0 4px 12px rgba(15, 23, 42, 0.25);
        }

        .journey-flight-marker {
          pointer-events: none;
        }

        .flight-marker-root {
          display: flex;
          flex-direction: column;
          align-items: center;
          justify-content: center;
          gap: 4px;
          transform: translate(-50%, -50%);
        }

        .flight-marker-plane {
          width: 40px;
          height: 40px;
          border-radius: 9999px;
          display: flex;
          align-items: center;
          justify-content: center;
          background: linear-gradient(
            135deg,
            rgba(37, 99, 235, 0.95),
            rgba(14, 165, 233, 0.9)
          );
          color: #fff;
          box-shadow: 0 8px 22px rgba(37, 99, 235, 0.45);
          transition: transform 0.12s linear;
          transform-origin: center;
        }

        .flight-marker-root--idle .flight-marker-plane {
          box-shadow: 0 4px 14px rgba(30, 41, 59, 0.35);
        }

        .flight-marker-label {
          padding: 2px 8px;
          border-radius: 9999px;
          background: rgba(15, 23, 42, 0.8);
          color: white;
          font-size: 10px;
          font-weight: 600;
          letter-spacing: 0.02em;
          white-space: nowrap;
        }

        .marker-container {
          position: relative;
          width: 40px;
          height: 40px;
          border-radius: 50% 50% 50% 0;
          border: 3px solid white;
          transform: rotate(-45deg);
          box-shadow: 0 2px 8px rgba(0, 0, 0, 0.3);
          display: flex;
          align-items: center;
          justify-content: center;
        }

        .marker-content {
          transform: rotate(45deg);
          color: white;
          font-size: 14px;
          font-weight: bold;
          text-align: center;
        }

        .marker-price {
          position: absolute;
          top: -25px;
          left: 50%;
          transform: translateX(-50%);
          background: white;
          color: #333;
          padding: 2px 6px;
          border-radius: 4px;
          font-size: 10px;
          white-space: nowrap;
          box-shadow: 0 1px 4px rgba(0, 0, 0, 0.2);
        }

        .marker-hotel {
          background: #3b82f6;
        }

        .marker-airport {
          background: #ef4444;
        }

        .marker-destination {
          background: #10b981;
        }

        .marker-default {
          background: #6b7280;
        }

        .mapboxgl-popup-content {
          padding: 0;
          border-radius: 8px;
          overflow: hidden;
          max-width: 300px;
        }

        .popup-content {
          min-width: 200px;
        }

        .popup-image {
          width: 100%;
          height: 120px;
          object-fit: cover;
        }

        .popup-body {
          padding: 12px;
        }

        .popup-title {
          font-size: 16px;
          font-weight: 600;
          margin: 0 0 4px 0;
          color: #1f2937;
        }

        .popup-description {
          font-size: 14px;
          color: #6b7280;
          margin: 0 0 8px 0;
        }

        .popup-price {
          font-size: 14px;
          font-weight: 600;
          color: #3b82f6;
        }

        .mapboxgl-ctrl-group {
          border-radius: 8px;
          overflow: hidden;
        }

        .mapboxgl-ctrl button {
          width: 36px;
          height: 36px;
        }
      `}</style>
    </div>
  );
}

export default MapboxMap;
