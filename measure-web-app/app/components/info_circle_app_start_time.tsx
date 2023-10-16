import React from 'react';


interface InfoCircleAppStartTimeProps {
  value: number;
  delta: number;
  title: string;
}

const InfoCircleAppStartTime = ({ value, delta, title }: InfoCircleAppStartTimeProps) => {
    return (
        <div className="flex flex-col items-center">
            <div className={`flex flex-col items-center justify-center w-64 aspect-square rounded-full border border-4 ${value < 800? 'border-green-400': value < 1200? 'border-yellow-400': 'border-red-400'}`}>
                <p className="text-black font-sans text-xl">{value}ms</p>
                <p className={`font-sans text-xl ${delta < 0? 'text-green-600': delta > 0? 'text-red-400': 'opacity-0'}`}>{delta}ms</p>
            </div>
            <div className="py-2"/>
            <p className="text-black font-display text-lg">{title}</p>
        </div>
  );
};

export default InfoCircleAppStartTime;