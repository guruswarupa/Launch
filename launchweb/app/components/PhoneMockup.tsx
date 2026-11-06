'use client';

import Image from 'next/image';
import { useEffect, useRef, useState } from 'react';

export default function PhoneMockup() {
  const [activePage, setActivePage] = useState(2);
  const [, setIsAnimating] = useState(false);
  const [touchStart, setTouchStart] = useState<number | null>(null);
  const [touchEnd, setTouchEnd] = useState<number | null>(null);
  const containerRef = useRef<HTMLDivElement>(null);

  // Handle touch/swipe gestures
  const handleTouchStart = (e: React.TouchEvent) => {
    setTouchEnd(null);
    setTouchStart(e.targetTouches[0].clientX);
  };

  const handleTouchMove = (e: React.TouchEvent) => {
    setTouchEnd(e.targetTouches[0].clientX);
  };

  const handleTouchEnd = () => {
    if (!touchStart || !touchEnd) return;
    
    const distance = touchStart - touchEnd;
    const isLeftSwipe = distance > 50;
    const isRightSwipe = distance < -50;
    
    if (isLeftSwipe) {
      setActivePage(prev => prev >= 3 ? 1 : prev + 1);
      setIsAnimating(true);
      setTimeout(() => setIsAnimating(false), 500);
    }
    
    if (isRightSwipe) {
      setActivePage(prev => prev <= 1 ? 3 : prev - 1);
      setIsAnimating(true);
      setTimeout(() => setIsAnimating(false), 500);
    }
    
    setTouchStart(null);
    setTouchEnd(null);
  };

  // Handle mouse drag for desktop
  const [mouseDownX, setMouseDownX] = useState(0);
  const handleMouseDown = (e: React.MouseEvent) => {
    setMouseDownX(e.clientX);
  };
  
  const handleMouseUp = (e: React.MouseEvent) => {
    if (!mouseDownX) return;
    
    const distance = mouseDownX - e.clientX;
    const isLeftSwipe = distance > 50;
    const isRightSwipe = distance < -50;
    
    if (isLeftSwipe) {
      setActivePage(prev => prev >= 3 ? 1 : prev + 1);
      setIsAnimating(true);
      setTimeout(() => setIsAnimating(false), 500);
    }
    
    if (isRightSwipe) {
      setActivePage(prev => prev <= 1 ? 3 : prev - 1);
      setIsAnimating(true);
      setTimeout(() => setIsAnimating(false), 500);
    }
    
    setMouseDownX(0);
  };

  // Handle keyboard navigation
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'ArrowLeft') {
        setActivePage(prev => prev >= 3 ? 1 : prev + 1);
        setIsAnimating(true);
        setTimeout(() => setIsAnimating(false), 500);
      } else if (e.key === 'ArrowRight') {
        setActivePage(prev => prev <= 1 ? 3 : prev - 1);
        setIsAnimating(true);
        setTimeout(() => setIsAnimating(false), 500);
      }
    };

    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, []);

  return (
    <div className="relative mx-auto">
      {/* Realistic Phone Frame - Samsung S23 Ultra style */}
      <div 
        ref={containerRef}
        className="relative w-[220px] h-[460px] sm:w-[240px] sm:h-[500px] bg-gradient-to-br from-slate-700 via-slate-800 to-slate-900 rounded-[2rem] border-[4px] border-slate-600 shadow-2xl overflow-hidden transition-all duration-500 hover:scale-105 hover:shadow-sky-500/30 select-none touch-none cursor-grab active:cursor-grabbing"
        onTouchStart={handleTouchStart}
        onTouchMove={handleTouchMove}
        onTouchEnd={handleTouchEnd}
        onMouseDown={handleMouseDown}
        onMouseUp={handleMouseUp}
        onMouseLeave={() => setMouseDownX(0)}
        role="region"
        aria-label="Interactive phone mockup - Swipe or use arrow keys to navigate"
        tabIndex={0}
      >
        {/* Screen notch/camera - centered punch hole */}
        <div className="absolute top-1 left-1/2 transform -translate-x-1/2 z-30">
          <div className="w-3 h-3 bg-slate-950 rounded-full border-2 border-slate-600 shadow-lg flex items-center justify-center">
            <div className="w-1.5 h-1.5 bg-slate-800 rounded-full"></div>
          </div>
        </div>
        
        {/* Power button */}
        <div className="absolute right-[-5px] top-20 w-[4px] h-16 bg-gradient-to-b from-slate-600 to-slate-700 rounded-r-sm"></div>
        
        {/* Volume buttons */}
        <div className="absolute left-[-5px] top-16 w-[4px] h-12 bg-gradient-to-b from-slate-600 to-slate-700 rounded-l-sm"></div>
        <div className="absolute left-[-5px] top-32 w-[4px] h-12 bg-gradient-to-b from-slate-600 to-slate-700 rounded-l-sm"></div>
        
        {/* Screen */}
        <div 
          className="absolute inset-0 bg-black overflow-hidden touch-none pt-5 select-none"
          onTouchStart={handleTouchStart}
          onTouchMove={handleTouchMove}
          onTouchEnd={handleTouchEnd}
          onDragStart={(e) => e.preventDefault()}
        >
          {/* Screen content with swipe animation */}
          <div className="relative w-full h-full select-none pointer-events-none">
            {/* Page 1: Left - Widgets Drawer */}
            <div 
              className={`transition-all duration-500 absolute inset-0 ${
                activePage === 1 
                  ? 'opacity-100 scale-100 translate-x-0' 
                  : activePage > 1 
                    ? 'opacity-0 scale-95 -translate-x-full' 
                    : 'opacity-0 scale-95 translate-x-full'
              }`}
            >
              <Image src="/leftpage.jpeg" alt="Widgets Drawer" fill sizes="300px" className="object-cover select-none pointer-events-none" draggable={false} />
            </div>

            {/* Page 2: Center - Home with Workspace & Focus Mode */}
            <div 
              className={`transition-all duration-500 absolute inset-0 ${
                activePage === 2 
                  ? 'opacity-100 scale-100 translate-x-0' 
                  : activePage > 2 
                    ? 'opacity-0 scale-95 -translate-x-full' 
                    : 'opacity-0 scale-95 translate-x-full'
              }`}
            >
              <Image src="/centerpage.jpeg" alt="Home Screen" fill sizes="300px" className="object-cover select-none pointer-events-none" draggable={false} />
            </div>

            {/* Page 3: Right - App Drawer */}
            <div 
              className={`transition-all duration-500 absolute inset-0 ${
                activePage === 3 
                  ? 'opacity-100 scale-100 translate-x-0' 
                  : activePage < 3 
                    ? 'opacity-0 scale-95 translate-x-full' 
                    : 'opacity-0 scale-95 -translate-x-full'
              }`}
            >
              <Image src="/rightpage.jpeg" alt="App Drawer" fill sizes="300px" className="object-cover select-none pointer-events-none" draggable={false} />
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
