"use client";

import { useState, useEffect } from "react";
import { Button } from "@/components/ui/button";
import {
  Command,
  CommandEmpty,
  CommandGroup,
  CommandInput,
  CommandItem,
} from "@/components/ui/command";
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from "@/components/ui/popover";
import { Check, ChevronsUpDown, MapPin } from "lucide-react";
import { cn } from "@/lib/utils";
import { AirportService } from "@/services/airport-service";
import type { Airport } from "@/types/api";

interface AirportSelectorProps {
  value?: number | null;
  onChange: (value: number | null) => void;
  placeholder?: string;
  className?: string;
  disabled?: boolean;
}

export function AirportSelector({
  value,
  onChange,
  placeholder = "Select airport",
  className,
  disabled = false,
}: AirportSelectorProps) {
  const [open, setOpen] = useState(false);
  const [airports, setAirports] = useState<Airport[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    loadAirports();
  }, []);

  const loadAirports = async () => {
    try {
      setLoading(true);
      setError(null);
      const data = await AirportService.getActiveAirports();
      setAirports(data);
      
      if (data.length === 0) {
        setError("No airports available");
      }
    } catch (error) {
      console.error("Failed to load airports:", error);
      setError("Failed to load airports");
      setAirports([]);
    } finally {
      setLoading(false);
    }
  };

  const selectedAirport = airports.find(
    (airport) => airport.airportId === value
  );

  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger asChild>
        <Button
          variant="outline"
          role="combobox"
          aria-expanded={open}
          className={cn("w-full justify-between", className)}
          disabled={disabled || loading}
        >
          {loading ? (
            "Loading airports..."
          ) : value && selectedAirport ? (
            <div className="flex items-center gap-2">
              <MapPin className="h-4 w-4 text-muted-foreground" />
              <div className="flex flex-col text-left">
                <span className="font-medium">{selectedAirport.iataCode}</span>
                <span className="text-xs text-muted-foreground">
                  {selectedAirport.name} • {selectedAirport.city}
                </span>
              </div>
            </div>
          ) : (
            placeholder
          )}
          <ChevronsUpDown className="ml-2 h-4 w-4 shrink-0 opacity-50" />
        </Button>
      </PopoverTrigger>
      <PopoverContent className="w-[400px] p-0">
        <Command shouldFilter={true}>
          <CommandInput placeholder="Search airports..." />
          <CommandEmpty>
            {error ? error : "No airport found."}
          </CommandEmpty>
          <CommandGroup className="max-h-[300px] overflow-y-auto">
            {airports.map((airport) => (
              <CommandItem
                key={airport.airportId}
                value={`${airport.iataCode} ${airport.name} ${airport.city} ${airport.country}`}
                onSelect={() => {
                  onChange(
                    airport.airportId === value ? null : airport.airportId
                  );
                  setOpen(false);
                }}
              >
                <Check
                  className={cn(
                    "mr-2 h-4 w-4",
                    value === airport.airportId ? "opacity-100" : "opacity-0"
                  )}
                />
                <div className="flex flex-col">
                  <div className="flex items-center gap-2">
                    <span className="font-medium">{airport.iataCode}</span>
                    <span className="text-sm">- {airport.name}</span>
                  </div>
                  <span className="text-xs text-muted-foreground">
                    {airport.city}, {airport.country}
                  </span>
                </div>
              </CommandItem>
            ))}
          </CommandGroup>
        </Command>
      </PopoverContent>
    </Popover>
  );
}
