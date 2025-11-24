"use client"

import * as React from "react"
import { Check, ChevronsUpDown } from "lucide-react"

import { cn } from "@/lib/utils"
import { Button } from "@/components/ui/button"
import {
  Command,
  CommandEmpty,
  CommandGroup,
  CommandInput,
  CommandItem,
  CommandList,
} from "@/components/ui/command"
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from "@/components/ui/popover"
import { countryService, type Country } from "@/lib/country-service"

interface CountrySelectorProps {
  value?: string
  onValueChange?: (value: string) => void
  disabled?: boolean
  placeholder?: string
  className?: string
}

export function CountrySelector({
  value,
  onValueChange,
  disabled = false,
  placeholder = "Select country...",
  className,
}: CountrySelectorProps) {
  const [open, setOpen] = React.useState(false)
  const [countries, setCountries] = React.useState<Country[]>([])
  const [loading, setLoading] = React.useState(true)

  React.useEffect(() => {
    countryService
      .getAllCountries()
      .then((data) => {
        setCountries(data)
        setLoading(false)
      })
      .catch((error) => {
        console.error("Failed to load countries:", error)
        setLoading(false)
      })
  }, [])

  const selectedCountry = React.useMemo(
    () => countries.find((country) => country.name.common === value),
    [countries, value]
  )

  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger asChild>
        <Button
          variant="outline"
          role="combobox"
          aria-expanded={open}
          disabled={disabled || loading}
          className={cn(
            "w-full justify-between bg-white border-gray-300 disabled:opacity-60",
            className
          )}
        >
          {selectedCountry ? (
            <div className="flex items-center gap-2">
              <img
                src={selectedCountry.flags.svg}
                alt={`${selectedCountry.name.common} flag`}
                className="h-4 w-6 object-cover rounded"
              />
              <span>{selectedCountry.name.common}</span>
              <span className="text-gray-500 text-xs">
                ({selectedCountry.cca2})
              </span>
            </div>
          ) : (
            <span className="text-gray-500">{loading ? "Loading..." : placeholder}</span>
          )}
          <ChevronsUpDown className="ml-2 h-4 w-4 shrink-0 opacity-50" />
        </Button>
      </PopoverTrigger>
      <PopoverContent className="w-full p-0" align="start">
        <Command>
          <CommandInput placeholder="Search country..." />
          <CommandList>
            <CommandEmpty>No country found.</CommandEmpty>
            <CommandGroup>
              {countries.map((country) => (
                <CommandItem
                  key={country.cca2}
                  value={country.name.common}
                  onSelect={(currentValue) => {
                    onValueChange?.(currentValue === value ? "" : currentValue)
                    setOpen(false)
                  }}
                >
                  <Check
                    className={cn(
                      "mr-2 h-4 w-4",
                      value === country.name.common ? "opacity-100" : "opacity-0"
                    )}
                  />
                  <img
                    src={country.flags.svg}
                    alt={`${country.name.common} flag`}
                    className="h-4 w-6 object-cover rounded mr-2"
                  />
                  <span>{country.name.common}</span>
                  <span className="ml-auto text-gray-500 text-xs">
                    {country.cca2}
                  </span>
                </CommandItem>
              ))}
            </CommandGroup>
          </CommandList>
        </Command>
      </PopoverContent>
    </Popover>
  )
}
