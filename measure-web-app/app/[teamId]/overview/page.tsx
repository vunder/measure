"use client"

import React, { useState } from 'react';
import Journey from "@/app/components/journey";
import MetricsOverview from '@/app/components/metrics_overview';
import { FiltersApiType } from '@/app/api/api_calls';
import Filters, { defaultSelectedFilters } from '@/app/components/filters';

export default function Overview({ params }: { params: { teamId: string } }) {
  const [selectedFilters, setSelectedFilters] = useState(defaultSelectedFilters);

  return (
    <div className="flex flex-col selection:bg-yellow-200/75 items-start p-24 pt-8">
      <div className="py-4" />
      <p className="font-display font-regular text-4xl max-w-6xl text-center">Overview</p>
      <div className="py-4" />

      <Filters
        teamId={params.teamId}
        filtersApiType={FiltersApiType.All}
        showCountries={false}
        showNetworkTypes={false}
        showNetworkProviders={false}
        showNetworkGenerations={false}
        showLocales={false}
        showDeviceManufacturers={false}
        showDeviceNames={false}
        onFiltersChanged={(updatedFilters) => setSelectedFilters(updatedFilters)} />

      <div className="py-4" />

      {selectedFilters.ready &&
        <Journey
          teamId={params.teamId}
          appId={selectedFilters.selectedApp.id}
          bidirectional={false}
          startDate={selectedFilters.selectedStartDate}
          endDate={selectedFilters.selectedEndDate}
          appVersions={selectedFilters.selectedVersions} />}
      <div className="py-8" />

      {selectedFilters.ready &&
        <MetricsOverview
          appId={selectedFilters.selectedApp.id}
          startDate={selectedFilters.selectedStartDate}
          endDate={selectedFilters.selectedEndDate}
          appVersions={selectedFilters.selectedVersions} />}

    </div>
  )
}
